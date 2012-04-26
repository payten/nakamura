/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.proxy;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;

import org.apache.commons.io.input.ProxyInputStream;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.proxy.ProxyPostProcessor;
import org.sakaiproject.nakamura.api.proxy.ProxyResponse;
import org.sakaiproject.nakamura.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Will convert iCal to JSON.
 */
@Service(value = ProxyPostProcessor.class)
@org.apache.felix.scr.annotations.Component(label = "ProxyPostProcessor for iCal", description = "Post processor which converts iCal data to JSON.", immediate = true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai foundation"),
    @Property(name = "service.description", value = "Post processor which converts iCal data to JSON."),
    @Property(name = ICalProxyPostProcessor.MAX_RESPONSE_BYTES_PROP, 
              longValue=ICalProxyPostProcessor.DEFAULT_MAX_RESPONSE_BYTES,
              description="The maximum size (in bytes) that a response from a remote "
              + "server can be.")})
public class ICalProxyPostProcessor implements ProxyPostProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ICalProxyPostProcessor.class);
  
  /*package*/ static final long DEFAULT_MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
  /*package*/ static final String MAX_RESPONSE_BYTES_PROP = "sakai.proxy.ical.maxlength";
  
  /** The mime/content types we'll permit as responses from the remote server. */
  /*package*/ static final Set<String> ICAL_MIME_TYPES = ImmutableSet.of(
      // One can imagine it would be useful to accept text/plain so that people without
      // access to their server's mime types could still put up an .ics file. Or host on
      // a pastebin site.
      "text/plain",
      // Standard mime type
      "text/calendar",
      // vCalendar used to use this it seems...
      "text/x-vcalendar",
      "binary/octet-stream",
      "application/octet-stream");
  
  /** 
   * The key into the templateParams {@link Map} passed into the {@link #process} method.
   * Its value controls how the calendar is output. The default value is available as 
   * {@link #DEFAULT_RESPONSE_TYPE}. The permitted values are in 
   * {@link #OUTPUT_METHOD_NAMES}.
   */
  public static final String PARAM_RESPONSE_TYPE = "responsetype";
  /** The GET param name which enables calendar validation. */
  public static final String PARAM_VALIDATE = "validate";
  
  /** Message used to describe an iCalendar parser error. */
  private static final String ERR_ICAL_PARSE_FAILED = 
      "Couldn't parse iCalendar document";
  /** Message used if an error occurs when outputting a parsed calendar.*/
  private static final String ERR_ICAL_OUTPUT_FAILED = "Error outputting calendar.";
  
  /** The supported ways of outputting calendars. */
  private static final Map<String, CalendarDumper> OUTPUT_METHDOS = 
      ImmutableMap.<String, CalendarDumper>of(
          JsonCalendarDumper.NAME, JsonCalendarDumper.INSTANCE,
          ICalCalendarDumper.NAME, ICalCalendarDumper.INSTANCE);
  
  /** The default way to output calendars. */
  public static final String DEFAULT_RESPONSE_TYPE = JsonCalendarDumper.NAME;
  
  /** The permitted values of {@link #PARAM_RESPONSE_TYPE}. */
  public static final Set<String> OUTPUT_METHOD_NAMES = OUTPUT_METHDOS.keySet();
  
  // Instance variables
  
  /** The size in bytes of the longest response we'll proxy. */
  private long maxResponseLength = -1;
  
  @Activate
  protected void activate(Map<?,?> properties) {
    maxResponseLength = PropertiesUtil.toLong(properties.get(MAX_RESPONSE_BYTES_PROP), 
        DEFAULT_MAX_RESPONSE_BYTES);
  }
  
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.proxy.ProxyPostProcessor#getName()
   */
  public String getName() {
    return "iCal";
  }
  
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.proxy.ProxyPostProcessor#process(org.apache.sling.api.SlingHttpServletResponse,
   *      org.sakaiproject.nakamura.api.proxy.ProxyResponse)
   */
  public void process(Map<String, Object> templateParams,
      SlingHttpServletResponse response, ProxyResponse proxyResponse) throws IOException {
    
    checkState(maxResponseLength > 0, "maxResponseLength not initialised, or invalid: %s", 
        maxResponseLength);
    checkNotNull(response);
    checkNotNull(proxyResponse);
    if(templateParams == null) templateParams = ImmutableMap.of();
    
    try {
      validateResponseHeaders(proxyResponse);
      CalendarDumper dumper = getOutputMethod(castParams(templateParams));
      Calendar calendar = loadCalendar(proxyResponse);
      
      if(isValidationRequested(castParams(templateParams)) 
          || dumper.requiresValidCalendar()) {
        validateCalendar(calendar);
      }
      
      dumper.dump(calendar, response);
    }
    catch(ResponseFailedException e) {
      LOG.info(e.getMessage());
      e.populateHttpResponse(response);
      return;
    }
    catch(ParserException e) {
      LOG.info(ERR_ICAL_PARSE_FAILED, e);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, ERR_ICAL_PARSE_FAILED 
          + ": " + e.getMessage());
      return;
    }
  }
  
  /**
   * Checks that the response headers are OK. {@code false} is returned if the headers are
   * invalid and the {@code response} arg will have been populated with a suitable error
   * code and message. 
   * 
   * @return {@code true} if the response is valid, {@code false} otherwise.
   */
  private void validateResponseHeaders(ProxyResponse proxyResponse) 
      throws ResponseFailedException {
    
    // Ensure the response's Content-Type is one of the ones we permit
    String contentType = getFirstHeaderValue(proxyResponse, "Content-Type");
    
    if(contentType == null || !ICAL_MIME_TYPES.contains(
        contentType.toLowerCase())) {
      
      throw new ResponseFailedException(HttpServletResponse.SC_NOT_ACCEPTABLE, 
          String.format("Remote server responded with a Content-Type which is not " +
              "permitted. Got: %s, expected one of: %s",
              contentType, Joiner.on(", ").join(ICAL_MIME_TYPES)));
    }
  }
  
  private static String getFirstHeaderValue(ProxyResponse response, String name) {
    Map<String, String[]> headers = response.getResponseHeaders();
    if(headers == null)
      return null;
    
    String[] values = headers.get(name);
    if(values == null || values.length == 0)
      return null;
    String value = values[0];
    int splitPos = value.indexOf(';');
    if(splitPos != -1)
      return value.substring(0, splitPos);
    return value;
  }
  
  /** Pulls the first value associated with the provided key from the params map. */
  private String getParam(Map<String, RequestParameter[]> params, String key, 
      String defaultValue) {
    final RequestParameter[] values = params.get(key);
    if(values == null || values.length == 0 || values[0] == null)
        return defaultValue;
    else {
      String value = values[0].getString();
      if(value == null || value.trim().length() == 0)
        return defaultValue;
      return value;
    }
  }
  
  /**
   * Gets the {@link CalendarDumper} to use based on the query params of the request.
   * 
   * @param params The map of params passed to {@link #process}.
   * @return An appropriate dumper.
   * @throws ResponseFailedException If the response type param is unrecognised.
   */
  private CalendarDumper getOutputMethod(Map<String, RequestParameter[]> params) 
      throws ResponseFailedException {
    
    String methodName = getParam(params, PARAM_RESPONSE_TYPE, DEFAULT_RESPONSE_TYPE);
    
    CalendarDumper method = OUTPUT_METHDOS.get(methodName);
    if(method != null)
      return method;
    
    throw new ResponseFailedException(HttpServletResponse.SC_BAD_REQUEST, 
        String.format("Unknown %s: \"%s\", expected one of: %s", PARAM_RESPONSE_TYPE, 
            methodName, Joiner.on(", ").join(OUTPUT_METHOD_NAMES)));
  }
  
  private boolean isValidationRequested(Map<String, RequestParameter[]> params) {
    
    String validate = getParam(params, PARAM_VALIDATE, "false").toLowerCase();
    return "true".equals(validate);
  }
  
  /** 
   * Parse the response as an iCalendar feed, throwing an IOException if the response
   * is too long.
   * @throws ResponseFailedException 
   * */
  private Calendar loadCalendar(ProxyResponse response) throws IOException, 
      ParserException, ResponseFailedException {
    
    // We won't bother checking the Content-Length header directly as it may not be 
    // present, we'll just count the number of bytes read from the input stream.
    LengthLimitingInputStream input = new LengthLimitingInputStream(
        response.getResponseBodyAsInputStream(), this.maxResponseLength);
    
    try {
      return new CalendarBuilder().build(input);
    }
    // The LengthLimitingInputStream throws a StreamLengthException when a read() pushes
    // the number of read bytes over the limit
    catch(StreamLengthException e) {
      throw new ResponseFailedException(
          HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, 
          "The remote server's response was too long: " + e.getMessage());
    }
  }
  
  private void validateCalendar(Calendar calendar) throws ResponseFailedException {
    try {
      calendar.validate(true);
    }
    catch(ValidationException e) {
      throw new ResponseFailedException(HttpServletResponse.SC_NOT_ACCEPTABLE, 
          "Invalid Calendar Received: " + e.getMessage());
    }
  }
  
  /** 
   * The templateParams arg to {@link #process} seems to always have 
   * {@code RequestParameter[]} values, so this casts it appropriately.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, RequestParameter[]> castParams(Map<?, ?> params) {
    return ((Map<String, RequestParameter[]>)params);
  }
  
  /** Represents a means of outputting a {@link Calendar} as an HTTP response. */
  private interface CalendarDumper {
    /**
     * Outputs a representation of the calendar to the destination HTTP response object.
     * @param calendar The calendar to output.
     * @param destination The HTTP response to write to.
     */
    void dump(Calendar calendar, SlingHttpServletResponse destination) throws IOException;

    boolean requiresValidCalendar();
  }
  
  /** 
   * A {@link CalendarDumper} which outputs the calendar as JSON document.
   * 
   * <p>Note: The output from this dumper is the same as the JSON produced by the original 
   * implementation of {@link ICalProxyPostProcessor}.
   */
  private static final class JsonCalendarDumper implements CalendarDumper {
    
    static final JsonCalendarDumper INSTANCE = new JsonCalendarDumper();
    static final String NAME = "json";
    
    private JsonCalendarDumper() {}
    
    private void setupResponse(SlingHttpServletResponse response) {
      response.setCharacterEncoding(Charsets.UTF_8.name());
      response.setContentType("application/json");
    }
    
    public void dump(Calendar calendar, SlingHttpServletResponse response)
        throws IOException {
      
      setupResponse(response);
      
      // Build JSON response in memory to allow an error to be sent if something goes 
      // wrong.
      StringWriter writer = new StringWriter();
      JSONWriter json = new JSONWriter(writer);
      
      try {
        handleCalendar(json, calendar);
      } catch (JSONException e) {
        // A JSONException being thrown indicates a programmer error...
        throw new RuntimeException("Error converting calendar to JSON.", e);
      }
      
      response.getWriter().write(writer.toString());
    }
    
    private static void handleCalendar(JSONWriter json, Calendar calendar) 
        throws JSONException {
      json.object();
      json.key("vcalendar").object();
      json.key("vevents").array();

      ComponentList list = calendar.getComponents(Component.VEVENT);
      for(int i = 0, size = list.size(); i < size; ++i) {
        VEvent event = (VEvent) list.get(i);
        handleComponent(json, event);
      }
      
      json.endArray().endObject().endObject();
    }
    
    private static void handleComponent(JSONWriter json, VEvent event) 
        throws JSONException {
      json.object();
      PropertyList pList = event.getProperties();
      
      for(int i = 0, size = pList.size(); i < size; ++i) {
        net.fortuna.ical4j.model.Property p = 
            (net.fortuna.ical4j.model.Property) pList.get(i);
        json.key(p.getName());
        // Check if it is a date
        String value = p.getValue();
        if (p instanceof DateProperty) {
          DateProperty start = (DateProperty) p;
          value = DateUtils.iso8601(start.getDate());
        }

        json.value(value);
      }
      json.endObject();
    }

    @Override
    public boolean requiresValidCalendar() {
      // We don't really care if the calendar is technically invalid when outputting JSON.
      return false;
    }
  }
  
  private static final class ICalCalendarDumper implements CalendarDumper {

    static final String NAME = "ical";
    static final ICalCalendarDumper INSTANCE = new ICalCalendarDumper();
    
    private ICalCalendarDumper() {}
    
    @Override
    public void dump(Calendar calendar, SlingHttpServletResponse response)
        throws IOException {
      response.setCharacterEncoding(Charsets.UTF_8.name());
      response.setContentType("text/calendar");
      try {
        new CalendarOutputter().output(calendar, response.getWriter());
        response.flushBuffer();
      } catch (ValidationException e) {
        // This should never happen because the calendar will already have been validated.
        LOG.error(ERR_ICAL_OUTPUT_FAILED, e);
        throw new RuntimeException(ERR_ICAL_OUTPUT_FAILED, e);
      }
    }

    @Override
    public boolean requiresValidCalendar() {
      // CalendarOutputter() is rather picky about validity and seems to barf if the 
      // structure of the calendar does not meet the iCal rfc to the letter...
      return true;
    }
  }
  
  /**
   * An exception which is raised to abort the normal HTTP response and respond with an
   * error instead.
   */
  @SuppressWarnings("serial")
  private class ResponseFailedException extends Exception {
    
    private final int status;
    public ResponseFailedException(int status, String message) {
      super(message);
      this.status = status;
    }
    
    public void populateHttpResponse(SlingHttpServletResponse response) 
        throws IOException {
      response.sendError(status, getMessage());
    }
  }
  
  /**
   * An InputStream decorator which immediately throws a {@link StreamLengthException}
   * when more bytes than permitted are read from the stream.
   * 
   * <p>It should be noted that the intent is to blow up in a loud way when the limit is 
   * reached, rather than silently claiming the stream ended as Google Guava's 
   * LimitInputStream and commons IO's BoundedInputStreams do.
   */
  private static final class LengthLimitingInputStream extends ProxyInputStream {

    private final long maxLength;
    private long bytesRead;
    
    public LengthLimitingInputStream(InputStream in, long maxLength) {
      super(in);
      this.maxLength = maxLength;
      this.bytesRead = 0;
    }
    
    @Override
    public int read() throws IOException {
      return postReadHook(super.read());
    }
    
    @Override
    public int read(byte[] bts) throws IOException {
      return postReadHook(super.read(bts));
    }
    
    @Override
    public int read(byte[] bts, int st, int end) throws IOException {
      return postReadHook(super.read(bts, st, end));
    }
    
    @Override
    public long skip(long ln) throws IOException {
      long count = super.skip(ln);
      if(count > 0) {
        this.bytesRead += count;
        postRead();
      }
      return count;
    }
    
    /** Helper to increment bytesRead and call {@link #postRead()}*/
    private int postReadHook(int count) throws IOException {
      if(count > 0) {
        bytesRead += count;
        postRead();
      }
      return count;
    }
    
    private long getByteCount() {
      return bytesRead;
    }
    
    private void postRead() throws IOException {
      if(getByteCount() > this.maxLength) {
        throw new StreamLengthException(this.maxLength, getByteCount());
      }
    }
    
    @Override
    public synchronized void mark(int idx) {
      throw new RuntimeException("Not supported");
    }
    
    @Override
    public synchronized void reset() throws IOException {
      throw new RuntimeException("Not supported.");
    }
    
    @Override
    public boolean markSupported() {
      return false;
    }
  }
  
  /** Signals that too many bytes have been passed through a stream. */
  private static class StreamLengthException extends IOException {
    public StreamLengthException(long maxLength, long actualLength) {
      super(String.format("An attempt was made to pass more bytes than permitted " +
      		"through a stream. Max bytes: %s, processed count: %s", 
      		maxLength, actualLength));
    }
  }
}
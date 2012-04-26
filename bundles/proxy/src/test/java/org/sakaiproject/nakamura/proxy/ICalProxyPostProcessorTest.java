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

import static com.google.common.io.Resources.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparisons.*;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.both;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.proxy.ProxyResponse;

import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.class)
public class ICalProxyPostProcessorTest {

  private ICalProxyPostProcessor proxyPostProcessor;

  @Mock
  private SlingHttpServletResponse response;

  @Mock
  private ProxyResponse proxyResponse;

  @Mock
  private RequestParameter getQueryParameter;

  @Before
  public void setup() {
    proxyPostProcessor = new ICalProxyPostProcessor();
  }

  @Test
  public void ensureNameIsCorrect() {
    assertThat(proxyPostProcessor.getName(), equalTo("iCal"));
  }

  @Test
  public void rejectsResponseWithWrongContentType() throws IOException {
    // given
    proxyResponseHeadersContainBadContentType();

    // when
    proxyPostProcessor.activate(ImmutableMap.of());
    proxyPostProcessor.process(null, response, proxyResponse);

    // then
    verify4xxErrorReported();
  }

  @Test
  public void acceptsValidICalFeedAndRespondsWithJson() throws IOException, JSONException {

    // given
    Map<String, Object> getParams = queryParamsSpecifyingResponseType("json");
    proxyResponseHeadersContainValidContentType();
    proxyResponseContainingValidICalFeed();
    StringWriter writer = responseUsingStringWriter();

    // when
    proxyPostProcessor.activate(ImmutableMap.of());
    proxyPostProcessor.process(getParams, response, proxyResponse);

    // then
    verify(response).setContentType("application/json");
    stringContainsValidJsonDocument(writer.toString());
  }

  @Test
  public void acceptsValidICalFeedAndRespondsWithICal() throws IOException,
      JSONException, ParserException {

    // given
    Map<String, Object> getParams = queryParamsSpecifyingResponseType("ical");
    proxyResponseHeadersContainValidContentType();
    proxyResponseContainingValidICalFeed();
    StringWriter writer = responseUsingStringWriter();

    // when
    proxyPostProcessor.activate(ImmutableMap.of());
    proxyPostProcessor.process(getParams, response, proxyResponse);

    // then
    verify(response).setContentType("text/calendar");
    stringContainsValidICalData(writer.toString());
  }

  @Test
  public void rejectsStreamOfExcessiveLength() throws IOException {
    // given
    proxyResponseHeadersContainValidContentType();
    proxyResponseContainingValidICalFeed();
    // Config limiting stream length to 1K
    Map<String, String> oneKMaxStreamLengthConfig = ImmutableMap.of(
        ICalProxyPostProcessor.MAX_RESPONSE_BYTES_PROP, "1024"); // 1KiB

    // when
    proxyPostProcessor.activate(oneKMaxStreamLengthConfig);
    proxyPostProcessor.process(null, response, proxyResponse);

    // then
    verify4xxErrorReported();
    verify(response, never()).getWriter();
  }

  @Test
  public void rejectsInvalidIcalStream() throws IOException {
    // given
    proxyResponseHeadersContainValidContentType();
    proxyResponseContainingInvalidICalFeed();

    // when
    proxyPostProcessor.activate(ImmutableMap.of());
    proxyPostProcessor.process(null, response, proxyResponse);

    // then
    verify4xxErrorReported();
  }

  private void proxyResponseHeadersContainBadContentType() {
    Map<String, String[]> headers = ImmutableMap.of("Content-Type",
        new String[] { "application/x-fancy-new-data-format" });
    when(proxyResponse.getResponseHeaders()).thenReturn(headers);
  }

  private void proxyResponseHeadersContainValidContentType() {
    Map<String, String[]> headers = ImmutableMap.of("Content-Type",
        new String[] { "text/calendar" });
    when(proxyResponse.getResponseHeaders()).thenReturn(headers);
  }

  private Map<String, Object> queryParamsSpecifyingResponseType(String responseType) {
    when(getQueryParameter.getString()).thenReturn(responseType);
    return ImmutableMap.of(ICalProxyPostProcessor.PARAM_RESPONSE_TYPE,
        (Object) new RequestParameter[] { getQueryParameter });
  }

  private void proxyResponseContainingValidICalFeed() throws IOException {
    InputStream feedStream = newInputStreamSupplier(
        getResource(getClass(), "/ical/valid-calendar.ics")).getInput();
    when(proxyResponse.getResponseBodyAsInputStream()).thenReturn(feedStream);
  }

  private void proxyResponseContainingInvalidICalFeed() throws IOException {
    // Borrow one of the RSS test's XML files as the stream data. Clearly an XML doc won't
    // be a valid iCalendar doc.
    InputStream feedStream = newInputStreamSupplier(
        getResource(getClass(), "/sample-rss.xml")).getInput();
    when(proxyResponse.getResponseBodyAsInputStream()).thenReturn(feedStream);
  }

  private StringWriter responseUsingStringWriter() throws IOException {
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    return writer;
  }

  private void stringContainsValidJsonDocument(String json) throws JSONException {
    new JSONObject(json);
  }

  private void stringContainsValidICalData(String ical) throws IOException,
      ParserException {
    new CalendarBuilder().build(new StringReader(ical));
  }

  private void verify4xxErrorReported() throws IOException {
    verify(response).sendError(
        intThat(both(lessThan(500)).and(greaterThanOrEqualTo(400))), anyString());
  }
}

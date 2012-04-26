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
package org.sakaiproject.nakamura.util.telemetry;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.servlet.ServletException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;

@SlingServlet(paths = "/system/telemetry", generateComponent = true, generateService = true, methods = { "GET" })
public class TelemetryReportServlet extends SlingSafeMethodsServlet {
  
  @Override
  public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    response.setContentType("application/xml");
    write(response);
  }

  private void write(SlingHttpServletResponse response) throws IOException {
    StreamResult streamResult;
    SAXTransformerFactory tf;
    TransformerHandler hd;
    Transformer serializer;

    try {
      streamResult = new StreamResult(response.getWriter());
      tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
      hd = tf.newTransformerHandler();
      serializer = hd.getTransformer();

      serializer.setOutputProperty(OutputKeys.ENCODING,"utf-8");
      serializer.setOutputProperty(OutputKeys.INDENT,"yes");

      hd.setResult(streamResult);
      hd.startDocument();
      AttributesImpl atts = new AttributesImpl();
      hd.startElement("","","ResmonResults",atts);
      for ( String telemetryKey : TelemetryCounter.counters.keySet() ) {
        TelemetryEntryXml.write(hd, TelemetryCounter.counters.get(telemetryKey));
      }
      hd.endElement("","","ResmonResults");
      hd.endDocument();
    }
    catch(TransformerConfigurationException tce) {
      response.getWriter().println(tce.getMessage());
    }
    catch(SAXException se) {
      response.getWriter().println(se.getMessage());
    }
  }

}

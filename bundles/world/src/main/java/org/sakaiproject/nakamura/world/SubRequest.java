/*
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

package org.sakaiproject.nakamura.world;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.util.RequestInfo;
import org.sakaiproject.nakamura.util.RequestWrapper;
import org.sakaiproject.nakamura.util.ResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Dictionary;
import java.util.Enumeration;
import javax.servlet.ServletException;

public class SubRequest {

  private final SlingHttpServletRequest request;

  private final RequestInfo requestInfo;

  private final RequestWrapper requestWrapper;

  private final ResponseWrapper responseWrapper;

  private final JSONWriter write;

  private boolean includeFullResponse = false;

  public SubRequest(String url, String method, JSONObject parameters, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write)
          throws JSONException, MalformedURLException, URISyntaxException, UnsupportedEncodingException {
    JSONObject json = new JSONObject();
    json.put("url", url);
    json.put("method", method);
    parameters.put("_charset_", "utf-8");
    json.put("parameters", parameters);
    this.request = request;
    this.requestInfo = new RequestInfo(json);
    this.requestWrapper = new RequestWrapper(request, this.requestInfo);
    this.responseWrapper = new ResponseWrapper(response);
    this.write = write;
    RequestParameter includeFullResponseParam = request.getRequestParameter("SubRequest.includeFullResponse");
    if (includeFullResponseParam != null) {
      includeFullResponse = Boolean.valueOf(includeFullResponseParam.getString());
    }
  }

  public void doForward() throws IOException, ServletException, JSONException {
    this.request.getRequestDispatcher(this.requestInfo.getUrl()).forward(this.requestWrapper, this.responseWrapper);
    writeResponse(this.write, this.responseWrapper, this.requestInfo);
  }

  public String getBody() throws UnsupportedEncodingException {
    return responseWrapper.getDataAsString();
  }

  private void writeResponse(JSONWriter write, ResponseWrapper responseWrapper,
                             RequestInfo requestData) throws JSONException {
    try {
      String body = responseWrapper.getDataAsString();
      write.object();
      write.key("url");
      write.value(requestData.getUrl());
      write.key("success");
      write.value(true);
      write.key("status");
      write.value(responseWrapper.getResponseStatus());
      if (this.includeFullResponse) {
        write.key("body");
        write.value(body);
        write.key("headers");
        write.object();
        Dictionary<String, String> headers = responseWrapper.getResponseHeaders();
        Enumeration<String> keys = headers.keys();
        while (keys.hasMoreElements()) {
          String k = keys.nextElement();
          write.key(k);
          write.value(headers.get(k));
        }
        write.endObject();
      }
      write.endObject();
    } catch (UnsupportedEncodingException e) {
      writeFailedRequest(write, requestData);
    }
  }

  private void writeFailedRequest(JSONWriter write, RequestInfo requestData)
          throws JSONException {
    write.object();
    write.key("url");
    write.value(requestData.getUrl());
    write.key("success");
    write.value(false);
    write.endObject();
  }
}

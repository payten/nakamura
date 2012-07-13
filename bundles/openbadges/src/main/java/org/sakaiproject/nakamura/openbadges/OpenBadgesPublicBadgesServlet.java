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
package org.sakaiproject.nakamura.openbadges;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import org.apache.sling.commons.json.io.JSONWriter;

import javax.servlet.ServletException; 
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;


@SlingServlet(methods = { "GET" }, generateService = true, paths = { "/openbadges/badges" })
public class OpenBadgesPublicBadgesServlet extends SlingAllMethodsServlet {   
    
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    String userId = request.getParameter("userid"); 
    String groupId = request.getParameter("groupid");
    if (userId == null) {
        throw new ServletException("userid needs to be specified");
    }
    if (groupId == null) {
        throw new ServletException("groupid needs to be specified");
    }
    String backpackService = "http://beta.openbadges.org/";    
    String publicBadgesURL = backpackService + "displayer/"+userId+"/group/"+groupId+".json";
    
    HttpClient httpclient = new HttpClient();
    GetMethod method = new GetMethod(publicBadgesURL);
    method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));    
        
    try {        
        int statusCode = httpclient.executeMethod(method);
        
        if (statusCode != HttpStatus.SC_OK) {
            System.err.println("OpenBadges GET failed URL:"+publicBadgesURL);
            System.err.println("OpenBadges GET failed: " + method.getStatusLine());
            throw new Exception("Unable to access OpenBadges");
        }
               
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");              
        
        response.getWriter().write(method.getResponseBodyAsString());

    } catch (Exception e) {
        throw new ServletException(e);
    }     
  }
}

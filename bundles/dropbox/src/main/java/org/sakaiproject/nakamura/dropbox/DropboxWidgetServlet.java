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
package org.sakaiproject.nakamura.dropbox;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.sakaiproject.nakamura.api.lite.Session;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.jackrabbit.JackrabbitSparseUtils;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;



// Forbidden classes!


@SlingServlet(methods = "GET", paths = "/dropbox/widget", generateComponent=true)
public class DropboxWidgetServlet extends SlingAllMethodsServlet {  

  private static String DROPBOX_CONTENT_PATH_BASE = "/system/dropbox";  
      
  @Override
  protected void doPost(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException 
  {
    String widgetId = request.getParameter("widgetid");    
    if (widgetId == null) {
        throw new ServletException("widgetid needs to be specified");
    }
    
    Resource resource = request.getResource();
    
    Content content = resource.adaptTo(Content.class);
    ContentManager contentManager = resource.adaptTo(ContentManager.class);
    Session session = resource.adaptTo(Session.class);
    String user = request.getRemoteUser();
    
    Content dropbox = getDropbox(widgetId, contentManager, user);              
    
    Iterator it = request.getParameterMap().entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry pairs = (Map.Entry)it.next();        
        dropbox.setProperty((String) pairs.getKey(), pairs.getValue());
    }        
  } 
  
  @Override
  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException 
  {
    String widgetId = request.getParameter("widgetid");    
    if (widgetId == null) {
        throw new ServletException("widgetid needs to be specified");
    }
    
    Resource resource = request.getResource();
        
    ContentManager contentManager = resource.adaptTo(ContentManager.class);
    Session session = resource.adaptTo(Session.class);      
    String user = request.getRemoteUser();
            
    Content dropbox = getDropbox(widgetId, contentManager, user);
        
    try {
        ExtendedJSONWriter w = new ExtendedJSONWriter(response.getWriter());
        w.object();
        Iterator it = dropbox.getProperties().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            w.key((String) pairs.getKey());
            w.value((String) pairs.getValue());
        }            
        w.endObject();
    } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
    
  }
  
  
  private Content getDropbox(String widgetId, ContentManager cm, String user) throws ServletException{
    try {
        if (!cm.exists(DROPBOX_CONTENT_PATH_BASE)) {
            Content dropboxStore = new Content(DROPBOX_CONTENT_PATH_BASE, null);
            cm.update(dropboxStore);
        }
        if (!cm.exists(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId)) {
            Content widgetStore = new Content(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId, null);
            cm.update(widgetStore);        
        }
        return cm.get(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId);
    } catch (StorageClientException e) {    
      throw new ServletException(e.getMessage(), e);        
    } catch (AccessDeniedException e) {        
      throw new ServletException(e.getMessage(), e);
    }    
  }
}

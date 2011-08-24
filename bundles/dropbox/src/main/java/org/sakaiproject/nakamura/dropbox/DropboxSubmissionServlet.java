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
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.jcr.Node;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.request.RequestParameter;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;

import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;

import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
// Forbidden classes!


@SlingServlet(methods = "GET", paths = "/dropbox/submissions", generateComponent=true)
public class DropboxSubmissionServlet extends SlingAllMethodsServlet {  

  private static String DROPBOX_CONTENT_PATH_BASE = "/system/dropbox";  
    
  @Override
  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException 
  {
    String widgetId = request.getParameter("widgetid");    
    if (widgetId == null) {
        throw new ServletException("widgetid needs to be specified");
    }
            
    try {
        javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
        Session session = StorageClientUtils.adaptToSession(jcrSession);
        ContentManager contentManager = session.getContentManager();
        AccessControlManager accessControlManager = session.getAccessControlManager();
        String user = request.getRemoteUser();

        
        Content dropbox = getDropbox(widgetId, contentManager);    
        Content submission = contentManager.get(dropbox.getPath() + "/" + user);
    
        if (submission != null) {
            ExtendedJSONWriter w = new ExtendedJSONWriter(response.getWriter());
//            w.object();
//            Iterator it = submission.getProperties().entrySet().iterator();
//            while (it.hasNext()) {
//                Map.Entry pairs = (Map.Entry)it.next();
//                w.key((String) pairs.getKey());
//                w.value((String) pairs.getValue());
//            }            
//            w.endObject();            
            ExtendedJSONWriter.writeContentTreeToWriter(w, submission, 1);
        } else {
            PrintWriter out = response.getWriter();
            out.print("{}");            
        }
    } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ClientPoolException e) {      
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
    }    
    
  }
  
  
  @Override
  protected void doPost(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException 
  {
    String widgetId = request.getParameter("widgetid");    
    if (widgetId == null) {
        throw new ServletException("widgetid needs to be specified");
    }
    
    try {
        javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
        Session session = StorageClientUtils.adaptToSession(jcrSession);
        ContentManager contentManager = session.getContentManager();
        AccessControlManager accessControlManager = session.getAccessControlManager();
        AuthorizableManager authorizableManager = session.getAuthorizableManager();

        String user = request.getRemoteUser();      
        Authorizable au = authorizableManager.findAuthorizable(user);

        Content dropbox = getDropbox(widgetId, contentManager);
        

        for (Map.Entry<String, RequestParameter[]> e : request.getRequestParameterMap().entrySet()) {
            for (RequestParameter p : e.getValue()) {
              if (!p.isFormField()) {       
                String contentType = getContentType(p);  
                Map<String, Object> contentProperties = new HashMap<String, Object>();
                contentProperties.put(FilesConstants.POOLED_CONTENT_FILENAME, p.getFileName());
                contentProperties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, FilesConstants.POOLED_CONTENT_RT);
                contentProperties.put(FilesConstants.POOLED_CONTENT_CREATED_FOR, au.getId());
                contentProperties.put(FilesConstants.POOLED_NEEDS_PROCESSING, "true");
                contentProperties.put(Content.MIMETYPE_FIELD, contentType);
                contentProperties.put(FilesConstants.POOLED_CONTENT_USER_MANAGER, new String[]{au.getId()});    

                Content submission = new Content(dropbox.getPath() + "/" + user, contentProperties);                
                                
                if (contentManager.exists(submission.getPath())) {
                    // create a new version if it exists.. then update properties
                    contentManager.writeBody(submission.getPath(), p.getInputStream());
                    contentManager.saveVersion(submission.getPath());
                    contentManager.update(submission);
                } else {
                    // first version... so create the node... then upload the data
                    contentManager.update(submission);
                    contentManager.writeBody(submission.getPath(), p.getInputStream());
                }
                
                break;
              }
            }
        }        
    } catch (ClientPoolException e) {      
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
    }    
  }
  
  
  private Content getDropbox(String widgetId, ContentManager cm) throws ServletException{
    try {        
        return cm.get(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId);
    } catch (StorageClientException e) {    
      throw new ServletException(e.getMessage(), e);        
    } catch (AccessDeniedException e) {        
      throw new ServletException(e.getMessage(), e);
    }    
  }  
  
private String getContentType(RequestParameter value) {
    String contentType = value.getContentType();
    if (contentType != null) {
      int idx = contentType.indexOf(';');
      if (idx > 0) {
        contentType = contentType.substring(0, idx);
      }
    }
    if (contentType == null || contentType.equals("application/octet-stream")) {
      // try to find a better content type
      contentType = getServletContext().getMimeType(value.getFileName());
      if (contentType == null || contentType.equals("application/octet-stream")) {
        contentType = "application/octet-stream";
      }
    }
    return contentType;
  }  
  
  
}

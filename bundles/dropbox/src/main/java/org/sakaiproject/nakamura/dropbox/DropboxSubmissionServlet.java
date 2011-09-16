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
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jcr.Node;
import javax.servlet.http.HttpServletResponse;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.sling.api.request.RequestParameter;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;

import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.Repository;

import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;

import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
// Forbidden classes!


@SlingServlet(methods = "GET", paths = "/dropbox/submissions", generateComponent=true)
public class DropboxSubmissionServlet extends SlingAllMethodsServlet {  

  @Reference
  protected ClusterTrackingService clusterTrackingService;    
  
  @Reference
  protected Repository repository;
    
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

        System.out.println("*************** GET " + user );
        
        Content dropbox = getDropbox(widgetId, contentManager);    
        Content submission = contentManager.get(dropbox.getPath() + "/submissions/" + user);
    
        System.out.println("*************** db path " + dropbox.getPath() );
        
        if (submission != null) {
            ExtendedJSONWriter w = new ExtendedJSONWriter(response.getWriter());            
            ExtendedJSONWriter.writeContentTreeToWriter(w, submission, 1);
        } else {
            PrintWriter out = response.getWriter();
            out.print("null");            
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

        Session adminSession = repository.loginAdministrative();
        AuthorizableManager adminAuthorizableManager = adminSession.getAuthorizableManager();
        ContentManager adminContentManager = adminSession.getContentManager();        
        
        String user = request.getRemoteUser(); 
        System.out.println("*************** POST " + user );
        Authorizable au = adminAuthorizableManager.findAuthorizable(user);                
               
        Content dropbox = getDropbox(widgetId, contentManager);
        
        // check submission window!        
        String utc_active_to_str = (String) dropbox.getProperty("utc_active_to");
        Date utc_active_to = new Date(Long.parseLong(utc_active_to_str.trim()));
        
        Date now = new Date();
        if (now.after(utc_active_to)) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Window for submission has passed");
            throw new ServletException("Window for submission has passed");
        }
        
        System.out.println("*************** POST " + user );
        System.out.println("*************** db path " + dropbox.getPath() );

        for (Map.Entry<String, RequestParameter[]> e : request.getRequestParameterMap().entrySet()) {
            for (RequestParameter p : e.getValue()) {
              if (!p.isFormField()) {                                                
                  
                String contentType = getContentType(p);  
                
                System.out.println("*************** type " + contentType);                                                                                                   
                
                Content submission;
                
                if (adminContentManager.exists(dropbox.getPath() + "/submissions/" + user)) {
                    submission = adminContentManager.get(dropbox.getPath() + "/submissions/" + user);
                    //get the content's poolid
                    String poolId = (String) submission.getProperty("poolId");
                    System.out.println("*************** poolid " + poolId); 
                    //get the content's content
                    Content content = adminContentManager.get(poolId);                    
                    // update other things
                    content.setProperty(FilesConstants.POOLED_NEEDS_PROCESSING, "true");
                    content.setProperty(Content.MIMETYPE_FIELD, contentType);                    
                    submission.setProperty("filename", p.getFileName());                    
                    
                    // create a new version if it exists.. then update properties
                    adminContentManager.writeBody(poolId, p.getInputStream());               
                    adminContentManager.update(content);
                    adminContentManager.saveVersion(content.getPath());                                                                                
                    adminContentManager.update(submission);                                       
                } else {
                    // first version... so create the node... then upload the data
                    String poolId = generatePoolId();
                    
                    // submission stub propertes
                    Map<String, Object> submissionProperties = new HashMap<String, Object>();
                    submissionProperties.put("filename", p.getFileName()); 
                    submissionProperties.put("poolId", poolId); 
                    submission = new Content(dropbox.getPath() + "/submissions/" + user, submissionProperties);
                    
                    String newFileName = (String) dropbox.getProperty("title") + "_"+user+"_";
                    submission.setProperty("contentName", newFileName);                    
                    
                    // create submission stub                    
                    adminContentManager.update(submission);
                    
                    // create the file and set content properties
                    Map<String, Object> contentProperties = new HashMap<String, Object>();
                    // set the title                                        
                    contentProperties.put(FilesConstants.POOLED_CONTENT_FILENAME, newFileName);
                    
                    // set other things                    
                    contentProperties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, FilesConstants.POOLED_CONTENT_RT);
                    contentProperties.put(FilesConstants.POOLED_CONTENT_CREATED_FOR, au.getId());
                    contentProperties.put(FilesConstants.POOLED_NEEDS_PROCESSING, "true");
                    contentProperties.put(Content.MIMETYPE_FIELD, contentType);
                    contentProperties.put(FilesConstants.POOLED_CONTENT_USER_MANAGER, new String[]{au.getId()});                    
                    
                    Content content = new Content(poolId, contentProperties);
                    adminContentManager.update(content);
                    adminContentManager.writeBody(poolId, p.getInputStream());                    
                    
//                    List<AclModification> modifications = new ArrayList<AclModification>();
//                    AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
//                    AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
//                    accessControlManager.setAcl(Security.ZONE_CONTENT, poolId, modifications.toArray(new AclModification[modifications.size()]));
                }
                
                ExtendedJSONWriter w = new ExtendedJSONWriter(response.getWriter());    
                ExtendedJSONWriter.writeContentTreeToWriter(w, submission, 0);                
                                
                
                break;
              }
            }
        }        
    } catch (NoSuchAlgorithmException e) {      
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ClientPoolException e) {      
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (StorageClientException e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (AccessDeniedException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
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

  private String generatePoolId() throws UnsupportedEncodingException,
      NoSuchAlgorithmException {
    return clusterTrackingService.getClusterUniqueId();
  }
  
  
}

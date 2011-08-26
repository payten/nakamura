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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.sakaiproject.nakamura.api.lite.Session;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.jackrabbit.JackrabbitSparseUtils;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.Repository;

import org.apache.felix.scr.annotations.Reference;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;

// Forbidden classes!


@SlingServlet(methods = "GET", paths = "/dropbox/widget", generateComponent=true)
public class DropboxWidgetServlet extends SlingAllMethodsServlet {  

  @Reference
  protected Repository repository;    
    
  private static String DROPBOX_CONTENT_PATH_BASE = "/system/dropbox"; 
  private static ArrayList<String> DROPBOX_WRITABLE_PARAMS = new ArrayList<String>() {{
    add("title");
    add("deadline");    
  }};  
      
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

        String user = request.getRemoteUser();

        Content dropbox = getDropbox(widgetId, "", user);              

        Iterator it = request.getParameterMap().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();        
            dropbox.setProperty((String) pairs.getKey(), request.getParameter((String) pairs.getKey()));
        }
        
        contentManager.update(dropbox);
    } catch (StorageClientException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (AccessDeniedException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
            
  } 
  
  @Override
  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException 
  {
    String widgetId = request.getParameter("widgetid");    
    if (widgetId == null) {        
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "path parameter not specified");
        return;
    }
    
    String widgetPath = request.getParameter("path");    
    if (widgetPath == null) {        
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "widgetPath not specified");
        return;
    }    
            
    try {        
        javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
        Session session = StorageClientUtils.adaptToSession(jcrSession);
        ContentManager contentManager = session.getContentManager();
        AccessControlManager accessControlManager = session.getAccessControlManager();

        String user = request.getRemoteUser();

        Content dropbox = getDropbox(widgetId, widgetPath, user);
    
    
        ExtendedJSONWriter w = new ExtendedJSONWriter(response.getWriter());
        
        // depending on access... give them some widget data
        System.out.println("*************** user " + user);
        System.out.println("*************** _createdBy " + dropbox.getProperty("_createdBy"));
        System.out.println("*************** widgetCreator " + dropbox.getProperty("widgetCreator"));
        if (user.equals(dropbox.getProperty("widgetCreator"))) {
           ExtendedJSONWriter.writeContentTreeToWriter(w, dropbox, 2);    
        } else {
           ExtendedJSONWriter.writeContentTreeToWriter(w, dropbox, 0);
        }
        
    } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (StorageClientException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
    
  }
  
  
  private Content getDropbox(String widgetId, String widgetPath, String user) throws ServletException{
    try {
            
        Session adminSession = repository.loginAdministrative();
        AuthorizableManager adminAuthorizableManager = adminSession.getAuthorizableManager();
        ContentManager adminContentManager = adminSession.getContentManager();        
        AccessControlManager accessControlManager = adminSession.getAccessControlManager();

        if (!adminContentManager.exists(DROPBOX_CONTENT_PATH_BASE)) {
            Content dropboxStore = new Content(DROPBOX_CONTENT_PATH_BASE, null);
            adminContentManager.update(dropboxStore);
            List<AclModification> modifications = new ArrayList<AclModification>();            
            AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
            AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
            AclModification.addAcl(true, Permissions.CAN_WRITE, Group.EVERYONE, modifications);
            AclModification.addAcl(true, Permissions.CAN_READ, Group.EVERYONE, modifications);
            accessControlManager.setAcl(Security.ZONE_CONTENT, dropboxStore.getPath(), modifications.toArray(new AclModification[modifications.size()]));            
        }
        if (!adminContentManager.exists(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId)) {
            Content widgetStore = new Content(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId, null);
            widgetStore.setProperty("widgetPath", widgetPath);
            widgetStore.setProperty("widgetCreator", user);
            adminContentManager.update(widgetStore);        
            List<AclModification> modifications = new ArrayList<AclModification>();            
            AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
            AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
            AclModification.addAcl(true, Permissions.CAN_WRITE, Group.EVERYONE, modifications);                        
            AclModification.addAcl(true, Permissions.CAN_READ, Group.EVERYONE, modifications);
            accessControlManager.setAcl(Security.ZONE_CONTENT, widgetStore.getPath(), modifications.toArray(new AclModification[modifications.size()]));                     
        }
        return adminContentManager.get(DROPBOX_CONTENT_PATH_BASE + "/" + widgetId);
    } catch (StorageClientException e) {    
      throw new ServletException(e.getMessage(), e);        
    } catch (AccessDeniedException e) {        
      throw new ServletException(e.getMessage(), e);
    }    
  }
}

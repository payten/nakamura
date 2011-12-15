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

package org.sakaiproject.nakamura.user.lite.servlet;

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permission;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Enumeration;
import java.util.List;

@ServiceDocumentation(name = "Update User ACE Servlet", okForVersion = "1.1",
        description = "Updates a user's Access Control Entry (ACE). Maps on to nodes of resourceType sparse/user " +
                "with the modifyAce selector. This servlet responds at " +
                "/system/userManager/user/suzy.modifyAce.html",
        shortDescription = "Update a user ACE",
        bindings = @ServiceBinding(type = BindingType.TYPE, bindings = {"sparse/user"},
                selectors = @ServiceSelector(name = "update", description = "Updates the ACE of a user"),
                extensions = @ServiceExtension(name = "html", description = "Posts produce html containing the update status")),
        methods = @ServiceMethod(name = "POST",
                description = {"Updates a user ACE",
                        "Example<br>" +
                                "<pre>curl -FprincipalId=everyone -Fprivilege@jcr:read=granted http://localhost:8080/system/userManager/user/suzy.modifyAce.html</pre>"},
                parameters = {
                        @ServiceParameter(name = "principalId", description = "The principalId that will receive the specified privilege"),
                        @ServiceParameter(name = "privilege", description = "The privilege to grant, one of: <br>" +
                                "@jcr:read=granted|denied|none <br> " +
                                "@jcr:write=granted|denied|none <br>" +
                                "@jcr:delete=granted|denied|none <br>"
                        )
                },
                response = {
                        @ServiceResponse(code = 200, description = "Success, HTML describing status is returned."),
                        @ServiceResponse(code = 404, description = "User was not found."),
                        @ServiceResponse(code = 500, description = "Failure with HTML explanation.")}
        ))
@SlingServlet(resourceTypes = {"sparse/user"}, methods = {"POST"}, selectors = {"modifyAce"})
public class LiteModifyUserAceServlet extends LiteAbstractUserPostServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteModifyUserAceServlet.class);

  @SuppressWarnings({"deprecation"})
  @Override
  protected void handleOperation(SlingHttpServletRequest request,
                                 HtmlResponse htmlResponse, List<Modification> changes)
          throws StorageClientException, AccessDeniedException {

    Authorizable authorizable = null;
    Resource resource = request.getResource();
    if (resource != null) {
      authorizable = resource.adaptTo(Authorizable.class);
    }

    // check that the user was located.
    if (authorizable == null) {
      throw new ResourceNotFoundException(
              "User to update could not be determined");
    }

    // get a session
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
    if (session == null) {
      throw new StorageClientException("Sparse Session not found");
    }

    // get the principal that will get the specified privilege
    String principalId = request.getParameter("principalId");
    if (principalId == null) {
      throw new StorageClientException("principalId was not submitted.");
    }

    // figure out the specified privilege
    Enumeration<?> parameterNames = request.getParameterNames();
    List<AclModification> aclModifications = Lists.newArrayList();
    while (parameterNames.hasMoreElements()) {
      Object nextElement = parameterNames.nextElement();
      if (nextElement instanceof String) {
        String paramName = (String) nextElement;
        if (paramName.startsWith("privilege@")) {
          String privilegeName = paramName.substring("privilege@".length());
          Permission permission = getPermission(privilegeName);
          String parameterValue = request.getParameter(paramName);
          if (parameterValue != null && parameterValue.length() > 0) {
            if ("granted".equals(parameterValue) && permission != null) {
              LOGGER.info("{}:{}:{}:{}", new Object[]{authorizable.getId(), principalId, "granted", permission.getName()});
              AclModification.addAcl(true, permission, principalId, aclModifications);
            } else if ("denied".equals(parameterValue) && permission != null) {
              LOGGER.info("{}:{}:{}:{}", new Object[]{authorizable.getId(), principalId, "denied", permission.getName()});
              AclModification.addAcl(false, permission, principalId, aclModifications);
            } else if ("none".equals(parameterValue)) {
              LOGGER.info("{}:{}:{}:{}", new Object[]{authorizable.getId(), principalId, "cleared", "all"});
              AclModification.removeAcl(true, Permissions.ALL, principalId, aclModifications);
              AclModification.removeAcl(false, Permissions.ALL, principalId, aclModifications);
            }
          }
        }
      }
    }

    // save the changes
    AccessControlManager accessControlManager = session.getAccessControlManager();
    accessControlManager.setAcl(Security.ZONE_AUTHORIZABLES, authorizable.getId(),
            aclModifications.toArray(new AclModification[aclModifications.size()]));

  }

  private Permission getPermission(String privilegeName) {
    return Permissions.parse(privilegeName);
  }
}

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
package org.sakaiproject.nakamura.resource.lite.servlet.post;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_SUPER_TYPE_PROPERTY;
import static org.apache.sling.servlets.post.SlingPostConstants.OPERATION_IMPORT;
import static org.apache.sling.servlets.post.SlingPostConstants.RP_CONTENT;
import static org.apache.sling.servlets.post.SlingPostConstants.RP_CONTENT_FILE;
import static org.apache.sling.servlets.post.SlingPostConstants.RP_OPERATION;
import static org.sakaiproject.nakamura.api.resource.lite.SparseContentResource.SPARSE_CONTENT_RT;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.SparseNonExistingResource;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet allows creating Sparse-stored children beneath a Sparse-stored path.
 * It inspects a POST to a non-existing Sling Resource, and if the new Resource's nearest
 * existing parent is stored in Sparse, then the new Resource is directed to Sparse
 * as well. If the nearest existing parent is stored elsewhere, the servlet opts out
 * and lets Sling do its default handling.
 */
@SlingServlet(methods = "POST", resourceTypes = {"sling/nonexisting"})
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Handle non-existing resources whose path puts them in sparse.")})
public class SparseCreateServlet extends SlingAllMethodsServlet implements OptingServlet {
  private static final long serialVersionUID = -6590959255525049482L;
  private static final Logger LOGGER = LoggerFactory.getLogger(SparseCreateServlet.class);
  public static final String CONTENT_TARGET_PATH_ATTRIBUTE = SparseCreateServlet.class.getName() + ".contentTargetPath";

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    final Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
    if (session == null) {
      LOGGER.warn("Unable to get a Sparse session while handling POST to {}", request.getPathInfo());
      return;
    }

    try {
      ContentManager contentManager = session.getContentManager();
      final String targetPath = (String) request.getAttribute(CONTENT_TARGET_PATH_ATTRIBUTE);
      SparseNonExistingResource resourceWrapper = new SparseNonExistingResource(request.getResource(),
          targetPath, session, contentManager);
      RequestDispatcherOptions options = new RequestDispatcherOptions();
      request.getRequestDispatcher(resourceWrapper, options).forward(request, response);
    } catch (StorageClientException e) {
      LOGGER.warn("No content manager", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  // ---------- OptingServlet interface ----------------------------------------
  public boolean accepts(SlingHttpServletRequest request) {
    // check for a parent resource that lives in sparse
    String path = request.getResource().getPath();
    if (!StringUtils.isBlank(path)) {
      ResourceResolver resourceResolver = request.getResourceResolver();
      // search each parent to see if we should store this resource in sparse
      String parentPath = path;
      Resource parentResource = null;
      // loop while we haven't found a parent resource and the parent path hasn't hit the
      // root yet
      while (!parentPath.equalsIgnoreCase("/") && !StringUtils.isBlank(parentPath)) {
        parentResource = resourceResolver.getResource(parentPath);
        if (parentResource == null) {
          // get the next parent and loop again
          parentPath = PathUtils.getParentReference(parentPath);
        } else {
          // if the parent resource lives in sparse, we should try to save this resource
          // in sparse.
          Content parentContent = parentResource.adaptTo(Content.class);
          if (parentContent != null
              && SPARSE_CONTENT_RT.equals(parentResource.getResourceSuperType())) {
            // Because the final path of the resolved parent Resource might differ
            // from the path we started out with, we do not use the original raw path
            // obtained from the request. For example, the original URL may specify the
            // parent path "/~somebody/private" but then be resolved to
            // "a:somebody/private".
            String childPath = path.substring(parentPath.length());
            String contentTargetPath = parentContent.getPath() + childPath;
            LOGGER
                .info(
                    "Going to create Resource {} with Content {} starting from Resource {} with child path {}",
                    new Object[] { path, contentTargetPath, parentResource.getPath(),
                        childPath });
            request.setAttribute(CONTENT_TARGET_PATH_ATTRIBUTE, contentTargetPath);
            return true;
          } else {
            // a parent resource was found but we couldn't prove that it is a sparse
            // resource so we should stop processing
            return false;
          }
        }
      }

      // no parent resources found. check the request for resourceSuperType
      String resourceSuperType = getResourceSuperType(request);
      if (SPARSE_CONTENT_RT.equals(resourceSuperType)) {
        request.setAttribute(CONTENT_TARGET_PATH_ATTRIBUTE, request.getResource().getPath());
        return true;
      }
    }

    // default to returning false as it seems we shouldn't handle this resource.
    return false;
  }

  /**
   * Get the requested resource super type (sling:resourceSuperType) from the request.
   * This could be a request parameter or as part of content import (:operation=import).
   *
   * @param request
   * @return The found resource super type or null if none could be found.
   */
  private String getResourceSuperType(SlingHttpServletRequest request) {
    String resourceSuperType = request.getParameter(SLING_RESOURCE_SUPER_TYPE_PROPERTY);
    if (resourceSuperType == null && OPERATION_IMPORT.equals(request.getParameter(RP_OPERATION))) {
      InputStream contentStream = null;
      String content = request.getParameter(RP_CONTENT);
      try {
        if (content == null) {
          RequestParameter contentFile = request
              .getRequestParameter(RP_CONTENT_FILE);
          if (contentFile != null) {
            contentStream = contentFile.getInputStream();
            content = IOUtils.toString(contentStream, "UTF-8");
          }
        }

        if (content != null) {
          JSONObject json = new JSONObject(content);
          if (json.has(SLING_RESOURCE_SUPER_TYPE_PROPERTY)) {
            resourceSuperType = json.getString(SLING_RESOURCE_SUPER_TYPE_PROPERTY);
          }
        }
      } catch (IOException e) {
        LOGGER.warn("Error extracting resource super type: " + e.getMessage(), e);
      } catch (JSONException e) {
        LOGGER.warn("Error extracting resource super type: " + e.getMessage(), e);
      }
    }
    return resourceSuperType;
  }
}

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
package org.sakaiproject.nakamura.files.pool;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 *
 */
@Service(value=DefaultServletDelegate.class)
@Component(immediate=true, enabled=true, metatype=true)
public class GetAlternativeContentPoolStreamServlet extends SlingSafeMethodsServlet
    implements DefaultServletDelegate {
  /**
   *
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(GetAlternativeContentPoolStreamServlet.class);
  private static final long serialVersionUID = 6605017133790005483L;
  private static final Set<String> RESERVED_SELECTORS = new HashSet<String>();
  static {
    RESERVED_SELECTORS.add("selector-used-elsewhere");
  }

  public void doDelegateGet(SlingHttpServletRequest request,
      SlingHttpServletResponse response) throws ServletException, IOException {
   doGet(request, response);
  }

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      Resource resource = request.getResource();
      Content node = resource.adaptTo(Content.class);
      ContentManager contentManager = resource.adaptTo(ContentManager.class);
      String alternativeStream = getAlternativeStream(request);
      
      StreamHelper streamHelper = new StreamHelper();
      
      ServletContext sc = null;
      try {
        sc = getServletContext();
      } catch ( IllegalStateException e ) {
        LOGGER.debug(e.getMessage(), e);
      }
      
      streamHelper.stream(request, contentManager, node, alternativeStream, response, resource, sc);
    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(),e);
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(),e);
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(),e);
      throw new ServletException(e.getMessage(), e);
    }
  }

  private String getAlternativeStream(SlingHttpServletRequest request) {
    // be sure to keep this logic in sync with accepts
    RequestPathInfo rpi = request.getRequestPathInfo();
    String alternativeStream = null;
    String[] selectors = rpi.getSelectors();
    if ((selectors == null || selectors.length == 0) && rpi.getExtension() == null) {
      String[] lastPieces = parseResourcePath(rpi);
      if (lastPieces != null && lastPieces.length == 3) {
        alternativeStream = parseResourcePath(rpi)[1];
      } else {
        LOGGER.debug("Found resource path with unexpected structure [size:{}]",
            lastPieces.length);
      }
    } else {
      LOGGER.debug("Resource should not have selectors or an extension");
    }
    return alternativeStream;
  }

  /**
   * Do not interfere with the default servlet's handling of streaming data, which kicks
   * in if no extension has been specified was specified in the request. (Sling servlet
   * resolution uses a servlet's declared list of "extensions" for score weighing, not for
   * filtering.)
   *
   * @see org.apache.sling.api.servlets.OptingServlet#accepts(org.apache.sling.api.SlingHttpServletRequest)
   */
  public boolean accepts(SlingHttpServletRequest request) {
    // be sure to keep this logic in sync with getAlternativeStream
    RequestPathInfo rpi = request.getRequestPathInfo();
    String[] selectors = rpi.getSelectors();
    if ((selectors == null || selectors.length == 0) && rpi.getExtension() == null) {
      String[] lastPieces = parseResourcePath(rpi);
      if (lastPieces != null && lastPieces.length == 3
          && !RESERVED_SELECTORS.contains(lastPieces[1])) {
        return true;
      }
    }
    return false;
  }

  private String[] parseResourcePath(RequestPathInfo rpi) {
    String[] lastPieces = null;
    String path = rpi.getResourcePath();
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash > 0 && lastSlash < path.length() - 1) {
      String resourcePathEnding = path.substring(lastSlash + 1);
      lastPieces = StringUtils.split(resourcePathEnding, ".", 4);
    }
    return lastPieces;
  }
}

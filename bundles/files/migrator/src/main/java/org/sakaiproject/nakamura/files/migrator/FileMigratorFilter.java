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
package org.sakaiproject.nakamura.files.migrator;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

@Component
@Service
@Properties(value = {
  @Property(name="pattern", value=".*"),
  @Property(name = "service.description", value = "Performs migrations as necessary on file content."),
  @Property(name = "service.vendor", value = "The Sakai Foundation"),
  @Property(name = "filter.scope", value = "request", propertyPrivate = true),
  @Property(name = "service.ranking", intValue = 10)})
public class FileMigratorFilter implements Filter {

  private static Logger LOGGER = LoggerFactory.getLogger(FileMigratorFilter.class);

  private Pattern pubspacePathPattern;

  @Reference(cardinality = ReferenceCardinality.OPTIONAL_UNARY, policy = ReferencePolicy.DYNAMIC)
  FileMigrationService fileMigrationService;

  @Reference
  Repository repository;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    pubspacePathPattern = Pattern.compile("^/~\\S+/(public/pubspace|private/privspace).*$");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    ServletRequest chainRequest = request;
    if(fileMigrationService != null
      && isRequestForPrivspaceOrPubspace((HttpServletRequest) request)) {
      LOGGER.debug("processing a pubspace or privspace request {}", ((HttpServletRequest) request).getPathInfo());
      migratePath(((HttpServletRequest) request).getPathInfo());
    }
    chain.doFilter(chainRequest, response);
  }

  private void migratePath(String requestPath) {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      String resourcePath = requestPath.substring(0, requestPath.lastIndexOf("space") + 5);
      LOGGER.debug("resource path is {}", resourcePath);
      String sparseInternalPath = PathUtils.toUserContentPath(resourcePath);
      Content userSpaceContent = adminSession.getContentManager().get(sparseInternalPath);
      if (userSpaceContent != null) {
        LOGGER.debug("got user space content at {}", userSpaceContent.getPath());
      } else {
        LOGGER.debug("user space content at {} is null");
      }
      if (fileMigrationService.fileContentNeedsMigration(userSpaceContent)) {
        LOGGER.debug("confirmed that user space content needs to be migrated {}", userSpaceContent.getPath());
        fileMigrationService.migrateFileContent(userSpaceContent);
      }
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage());
    } catch (ClientPoolException e) {
      LOGGER.error(e.getMessage());
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage());
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  @Override
  public void destroy() {
  }

  boolean isRequestForPrivspaceOrPubspace(HttpServletRequest request) {
    Matcher matcher = pubspacePathPattern.matcher(request.getPathInfo());
    return "GET".equals(request.getMethod()) && matcher.matches();
  }
}

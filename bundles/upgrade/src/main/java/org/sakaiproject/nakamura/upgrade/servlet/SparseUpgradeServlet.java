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

package org.sakaiproject.nakamura.upgrade.servlet;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.lite.Feedback;
import org.sakaiproject.nakamura.api.lite.MigrateContentService;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "Sparse Upgrade Servlet", okForVersion = "1.1",
        description = "Upgrades data stored in sparsemapcontent storage layer by running all the PropertyMigrator instances " +
                "that are registered. Note that the upgrade only works for JDBC storage clients. The upgrade may take a long " +
                "time to run; progress of the upgrade gets written to the response every so often and can be seen in realtime if " +
                "your client supports buffered HTTP requests. Use -N option in curl to enable this. Your system log will have " +
                "more detail on the progress of the upgrade.",
        shortDescription = "Upgrades data stored in sparsemapcontent",
        bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/sparseupgrade"),
        methods = @ServiceMethod(name = "POST",
                description = {"Upgrades data stored in sparsemapcontent",
                        "Example<br>" +
                                "<pre>curl -N -u admin:password -e http://localhost:8080 -FdryRun=false -FreindexAll=true -Flimit=5000 " +
                                "http://localhost:8080/system/sparseupgrade</pre>"},
                parameters = {
                        @ServiceParameter(name = "dryRun", description = "Whether this is a dry run or not; in dry run, no data is actually " +
                                "saved. Default=true."),
                        @ServiceParameter(name = "limit", description = "If dryRun is true, then process up to this many rows; no effect if " +
                                "dryRun is false. Default=Integer.MAX_VALUE (2147483647)."),
                        @ServiceParameter(name = "reindexAll", description = "If true, then reindex every row whether it's changed or not. " +
                                "This will also force reindexing of every row in Solr. Makes the upgrade take longer. Default=false.")
                },
                response = {
                        @ServiceResponse(code = 200, description = "Success, the upgrade ran with no issues."),
                        @ServiceResponse(code = 500, description = "Failure with HTML explanation.")}
        ))
@SlingServlet(paths = {"/system/sparseupgrade"}, generateComponent = true, generateService = true, methods = {"POST"})
public class SparseUpgradeServlet extends SlingAllMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparseUpgradeServlet.class);

  @Reference
  private MigrateContentService migrationService;

  @Reference
  private TagMigrator tagMigrator;

  @Override
  protected void doPost(SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
    try {

      // make sure user's an admin
      Session currentSession = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
      AuthorizableManager authorizableManager = currentSession.getAuthorizableManager();
      User currentUser = (User) authorizableManager.findAuthorizable(currentSession.getUserId());
      if (!currentUser.isAdmin()) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You must be an admin to run upgrades");
        return;
      }

      // collect our parameters
      boolean dryRun = true;
      boolean reindexAll = false;
      Integer limit = Integer.MAX_VALUE;

      RequestParameter dryRunParam = request.getRequestParameter("dryRun");
      if (dryRunParam != null) {
        dryRun = Boolean.valueOf(dryRunParam.getString());
      }
      RequestParameter reindexAllParam = request.getRequestParameter("reindexAll");
      if (reindexAllParam != null) {
        reindexAll = Boolean.valueOf(reindexAllParam.getString());
      }
      RequestParameter limitParam = request.getRequestParameter("limit");
      if (limitParam != null) {
        limit = Integer.parseInt(limitParam.getString());
      }

      String msg = "About to call migration service with dryRun = " + dryRun + "; limit = " + limit
              + "; reindexAll = " + reindexAll + "; check your server log for more detailed information.";
      writeToResponse(msg, response);
      LOGGER.info(msg);

      // do the actual migration
      this.migrationService.migrate(dryRun, limit, reindexAll, getFeedback(response));

      // migrate tags from JCR to Sparse
      this.tagMigrator.migrate(response, dryRun, reindexAll);

      // reindex solr if necessary
      if (reindexAll && !dryRun) {
        msg = "Reindexing all content and authorizables in Solr...";
        writeToResponse(msg, response);
        LOGGER.info(msg);
        reindexSolr(request);
      }

    } catch (Exception e) {
      LOGGER.error("Got exception processing sparse upgrade", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private Feedback getFeedback(final SlingHttpServletResponse response) {
    return new Feedback() {
      public void log(String format, Object... params) {
        LOGGER.info(MessageFormat.format(format, params));
      }

      public void exception(Throwable e) {
        String msg = "An exception occurred while migrating: " + e.getClass().getName() + ": " +
                e.getMessage() + "; check server log for more details.";
        writeToResponse(msg, response);
      }

      public void newLogFile(File currentFile) {
        LOGGER.info("Opening New Upgrade Log File {}  ", currentFile.getAbsoluteFile());
      }

      public void progress(boolean dryRun, long done, long toDo) {
        String msg = "Processed " + done + " of " + toDo + ", " + ((done * 100) / toDo) + "% complete, dryRun=" + dryRun;
        writeToResponse(msg, response);
      }

    };
  }

  private void reindexSolr(SlingHttpServletRequest request) throws StorageClientException {
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
    session.getContentManager().triggerRefreshAll();
    session.getAuthorizableManager().triggerRefreshAll();
  }

  static void writeToResponse(String msg, SlingHttpServletResponse response) {
    try {
      response.getWriter().write(msg + "\n");
      response.getWriter().flush(); // so the client sees updates
    } catch (IOException ioe) {
      LOGGER.error("Got IOException trying to write http response", ioe);
    }
  }
}

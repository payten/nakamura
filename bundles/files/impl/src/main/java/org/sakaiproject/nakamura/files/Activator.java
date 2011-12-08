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
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.files;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);
  private static final String[] FILES = { "/tags", "/tags/directory" };

  private ServiceTracker repoTracker;

  @Override
  public void start(BundleContext context) throws Exception {
    repoTracker = new RepositoryTracker(context);
    repoTracker.open();
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    repoTracker.close();
  }

  /**
   * Tracks when a Sparse Repository becomes available so /tags and /tags/directory can be
   * created.
   */
  class RepositoryTracker extends ServiceTracker {
    public RepositoryTracker(BundleContext context) {
      super(context, Repository.class.getName(), null);
    }

    @Override
    public Object addingService(ServiceReference reference) {
      Repository repo = (Repository) super.addingService(reference);
      // only run once per bundle start rather than every time the a Repository is added
      if (getTrackingCount() == 0) {
        Session adminSession = null;
        try {
          adminSession = repo.loginAdministrative();
          ContentManager cm = adminSession.getContentManager();
          AccessControlManager acm = adminSession.getAccessControlManager();

          boolean replace = false;
          boolean replaceProperties = true;
          boolean removeTree = false;

          LiteJsonImporter jsonImporter = new LiteJsonImporter();
          for (String file : FILES) {
            LOGGER.info(
                "Importing {}.json to {} [replace={}, replaceProperties={}, removeTree={}",
                new Object[] { file, file, replace, replaceProperties, removeTree });
            JSONObject json = readJsonFromUrl(context, file + ".json");
            jsonImporter.importContent(cm, json, file, replace, replaceProperties, removeTree, acm);
          }
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage(), e);
        } catch (StorageClientException e) {
          LOGGER.error(e.getMessage(), e);
        } catch (AccessDeniedException e) {
          LOGGER.error(e.getMessage(), e);
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        } catch (JSONException e) {
          LOGGER.error(e.getMessage(), e);
        } finally {
          if (adminSession != null) {
              try {
                adminSession.logout();
              } catch (ClientPoolException e) {
                // noop; nothing to do
              }
          }
        }
      }
      return repo;
    }

    private JSONObject readJsonFromUrl(BundleContext context, String url) throws IOException, JSONException {
      InputStream is = context.getBundle().getResource(url).openStream();
      try {
        BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
        StringBuilder jsonText = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
          jsonText.append((char) cp);
        }
        JSONObject json = new JSONObject(jsonText.toString());
        return json;
      } finally {
        is.close();
      }
    }
  }
}

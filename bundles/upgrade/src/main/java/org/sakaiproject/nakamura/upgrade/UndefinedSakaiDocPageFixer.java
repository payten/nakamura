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

package org.sakaiproject.nakamura.upgrade;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Service
@Component
public class UndefinedSakaiDocPageFixer implements PropertyMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(UndefinedSakaiDocPageFixer.class);

  private static final String PAGE_PROP = "page";

  @Reference
  private Repository repository;

  @Override
  public boolean migrate(String rowID, Map<String, Object> properties) {
    // KERN-2763: Some sakaidocs have a subnode called "undefined" not referenced in the structure0.
    // this fixes up those nodes so they're not orphaned.
    boolean handled = false;
    Object page = properties.get(PAGE_PROP);
    Object pathObj = properties.get("_path");
    if (page != null && pathObj != null) {
      String path = pathObj.toString();
      if ("undefined".equals(PathUtils.lastElement(path))) {
        Session session = null;
        try {
          session = repository.loginAdministrative();
          Content parent = session.getContentManager().get(PathUtils.getParentReference(path));
          if (parent != null) {
            LOGGER.debug("Got the parent of an undefined page node: " + parent.getPath());
            Object structureObj = parent.getProperty(FileMigrationService.STRUCTURE_ZERO);
            if (structureObj != null) {
              // parent is a real sakaidoc
              JSONObject structure0 = new JSONObject(structureObj.toString());
              JSONObject undefinedObj = null;
              try {
                structure0.getJSONObject("undefined");
              } catch (JSONException ignored) {
                // it doesn't exist
              }
              if (undefinedObj == null) {
                // undefined ref not yet on structure0, so add it
                undefinedObj = new JSONObject();
                undefinedObj.put("_ref", "undefined");
                undefinedObj.put("_title", "Untitled Page");
                undefinedObj.put("_order", 0);
                undefinedObj.put("_canSubedit", true);
                undefinedObj.put("_canEdit", true);
                undefinedObj.put("_poolpath", "/p/" + PathUtils.lastElement(parent.getPath()));
                JSONObject main = new JSONObject();
                main.put("_ref", "undefined");
                main.put("_order", 0);
                main.put("_title", "Untitled Page");
                main.put("_childCount", 0);
                main.put("_canSubedit", true);
                main.put("_canEdit", true);
                main.put("_poolpath", "/p/" + PathUtils.lastElement(parent.getPath()));
                main.put("_id", "main");
                undefinedObj.put("main", main);
                structure0.put("undefined", undefinedObj);

                parent.setProperty("structure0", structure0.toString());
                LOGGER.info("Updating new structure0 data for sakai doc at path "
                    + parent.getPath() + ":" + parent.getProperty("structure0").toString());

                // save changes to the parent
                session.getContentManager().update(parent);
                handled = true;
              }
            }
          }
        } catch (AccessDeniedException e) {
          LOGGER.error("admin session denied access?", e);
        } catch (ClientPoolException e) {
          LOGGER.error("storage client pool exception", e);
        } catch (StorageClientException e) {
          LOGGER.error("StorageClientException", e);
        } catch (JSONException e) {
          LOGGER.error("Invalid json data in structure0?", e);
        } finally {
          if (session != null) {
            try {
              session.logout();
            } catch (ClientPoolException e) {
              LOGGER.error("Unexpected exception logging out of session", e);
            }
          }
        }
      }
    }
    return handled;
  }

  @Override
  public String[] getDependencies() {
    return new String[]{};
  }

  @Override
  public String getName() {
    return UndefinedSakaiDocPageFixer.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

}

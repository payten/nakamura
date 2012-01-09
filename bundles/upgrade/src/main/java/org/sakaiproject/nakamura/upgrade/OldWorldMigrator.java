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
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.RemoveProperty;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Service
@Component
public class OldWorldMigrator implements PropertyMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OldWorldMigrator.class);

  @Reference
  private Repository repository;

  @Override
  public boolean migrate(String rowID, Map<String, Object> properties) {
    Object title = properties.get("sakai:group-title");
    if (title != null) {
      if (extractRolesArray(properties) != null) {
        handleMainGroup(properties);
        return true;
      }
    }

    Object pseudoGroup = properties.get("sakai:pseudoGroup");
    if (pseudoGroup != null && pseudoGroup instanceof Boolean && (Boolean) pseudoGroup) {
      handlePseudoGroup(properties);
      return true;
    }

    return false;
  }

  @Override
  public String[] getDependencies() {
    return new String[]{PseudoGroupTypeCorrector.class.getName(), PseudoGroupParentRenamer.class.getName()};
  }

  @Override
  public String getName() {
    return OldWorldMigrator.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

  private void handleMainGroup(Map<String, Object> properties) {
    Object groupName = properties.get("name");
    JSONArray rolesArray = extractRolesArray(properties);
    if (rolesArray != null) {
      try {
        JSONArray newRoles = new JSONArray();
        boolean migrationSuccess = false;
        for (int i = 0; i < rolesArray.length(); i++) {
          JSONObject role = rolesArray.getJSONObject(i);
          try {
            if (role.getString("roleTitle") != null) {
              LOGGER.debug("In legacy group " + groupName + ", migrating sakai:roles data; role = " + role.toString
                  (2));
              JSONObject newRole = new JSONObject();
              newRole.put("id", role.getString("id"));
              newRole.put("title", role.getString("title"));
              newRole.put("titlePlural", role.getString("roleTitle"));
              newRole.put("isManagerRole", role.getBoolean("allowManage"));

              // management roles get special treatment
              if (role.getBoolean("allowManage")) {
                // roles that manage are assumed to manage all other roles (but not themselves). 
                JSONArray manages = new JSONArray();
                for (int j = 0; j < rolesArray.length(); j++) {
                  if (j != i) {
                    JSONObject managedRole = rolesArray.getJSONObject(j);
                    manages.put(managedRole.getString("id"));
                  }
                }
                newRole.put("manages", manages);
              }

              LOGGER.debug("Role data after migration: " + newRole.toString(2));
              newRoles.put(i, newRole);
              migrationSuccess = true;
            }
          } catch (JSONException ignored) {
            LOGGER.debug("Group " + groupName + " has already been migrated");
            migrationSuccess = false;
          }
        }

        if (migrationSuccess) {
          // store the new roles structure
          String newPropVal = newRoles.toString();
          properties.put("sakai:roles", newPropVal);
          LOGGER.debug("On group " + groupName + " the new value for sakai:roles property = " + newPropVal);
        } else {
          LOGGER.debug("On group " + groupName + " migration of roles did not succeed or was not necessary. Role data" +
              " = " + rolesArray);
        }

      } catch (JSONException e) {
        LOGGER.error("JSON Exception reading sakai:roles from group " + groupName, e);
      }
    }
  }

  private void handlePseudoGroup(Map<String, Object> properties) {
    Object roleTitleObj = properties.get("sakai:group-title");
    Object roleID = properties.get("name");
    if (roleTitleObj != null && roleTitleObj instanceof String) {
      LOGGER.debug("Migrating legacy pseudogroup " + roleID);
      properties.put("sakai:role-title", roleTitleObj);
      properties.put("sakai:role-title-plural", roleTitleObj);
      properties.put("sakai:group-title", new RemoveProperty());
    }

    Object parentGroupID = properties.get("sakai:parent-group-id");
    if (parentGroupID != null && parentGroupID instanceof String
        && properties.get("sakai:parent-group-title") == null) {
      Session session = null;
      try {
        session = repository.loginAdministrative();
        Authorizable parent = session.getAuthorizableManager().findAuthorizable((String) parentGroupID);
        if (parent != null) {
          properties.put("sakai:parent-group-title", parent.getProperty("sakai:group-title"));
          try {
            JSONArray parentRoles = extractRolesArray(parent.getOriginalProperties());
            LOGGER.debug("Pseudogroup's parent " + parent.getProperty("name") + " has these sakai:roles: "
                + parentRoles.toString(2));
            updateRoleTitleFromParentGroup(properties, parentRoles, (String) parentGroupID);
          } catch (JSONException e) {
            LOGGER.warn("JSON error looking up title in parent's sakai:roles structure", e);
          }
        }
      } catch (AccessDeniedException e) {
        LOGGER.error("Error looking up pseudogroup's parent", e);
      } catch (ClientPoolException e) {
        LOGGER.error("Error looking up pseudogroup's parent", e);
      } catch (StorageClientException e) {
        LOGGER.error("Error looking up pseudogroup's parent", e);
      } finally {
        if (session != null) {
          try {
            session.logout();
          } catch (ClientPoolException e) {
            LOGGER.error("Error logging out of admin session", e);
          }
        }
      }
    }
  }

  private void updateRoleTitleFromParentGroup(Map<String, Object> properties, JSONArray parentRoles,
                                              String parentGroupID) {
    String pseudoGroupID = (String) properties.get("id");
    String roleID = pseudoGroupID.substring((parentGroupID + "-").length());
    String titlePlural = (String) properties.get("sakai:role-title-plural");
    LOGGER.debug("Pseudogroup " + pseudoGroupID + " corresponds to role " + roleID + " in parentGroup " +
        parentGroupID);
    for (int i = 0; i < parentRoles.length(); i++) {
      try {
        JSONObject role = parentRoles.getJSONObject(i);
        String id = role.getString("id");
        if (id != null && id.equals(roleID)) {
          // parent might have been upgraded already. Try the old roleTitle field first,
          // overriding with titlePlural if it exists.
          try {
            titlePlural = role.getString("roleTitle");
          } catch (JSONException ignored) {
          }
          try {
            titlePlural = role.getString("titlePlural");
          } catch (JSONException ignored) {
          }

          properties.put("sakai:role-title-plural", titlePlural);
          LOGGER.debug("Updated pseudogroup " + pseudoGroupID + " with sakai:role-title-plural:" + titlePlural);
          break;
        }
      } catch (JSONException e) {
        LOGGER.warn("Parent Group " + parentGroupID + " has malformed JSON in sakai:roles object");
      }
    }
  }

  private JSONArray extractRolesArray(Map<String, Object> properties) {
    Object rolesObj = properties.get("sakai:roles");
    JSONArray roles = null;
    if (rolesObj instanceof String) {
      try {
        roles = new JSONArray(rolesObj.toString());
      } catch (JSONException ignored) {
      }
    }
    return roles;
  }

}

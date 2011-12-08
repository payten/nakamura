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

package org.sakaiproject.nakamura.world.steps;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.world.SubRequest;
import org.sakaiproject.nakamura.world.WorldCreationServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;

public class RoleStep extends AbstractWorldCreationStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(RoleStep.class);

  private Map<String, JSONObject> subgroups = new HashMap<String, JSONObject>(); // map of role ID to subgroup data

  private JSONObject mainGroupUpdateData = new JSONObject();

  public RoleStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request,
                  SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {

    JSONArray roles;
    try {
      roles = this.worldTemplate.getJSONArray(WorldCreationServlet.PARAMS.roles.toString());
    } catch (JSONException ignored) {
      // no roles
      return;
    }

    preprocessRoles(roles);
    createManagerRelationships(roles);
    createMemberships();
    createSubgroups();
    updateMainGroup();
  }

  private void preprocessRoles(JSONArray roles) throws JSONException {

    String visibility = this.data.getString(WorldCreationServlet.PARAMS.visibility.toString());
    String joinability = this.data.getString(WorldCreationServlet.PARAMS.joinability.toString());

    this.mainGroupUpdateData.put("sakai:group-visible", visibility);
    this.mainGroupUpdateData.put("sakai:group-joinable", joinability);

    // first collect all our roles into the subgroup map
    for (int i = 0; i < roles.length(); i++) {
      JSONObject role = new JSONObject(roles.getString(i)); // roles comes to us as an array of strings because of Sling quirk
      String mainGroupID = this.data.getString(WorldCreationServlet.PARAMS.id.toString());
      String roleID = role.getString(WorldCreationServlet.PARAMS.id.toString());
      String subGroupID = mainGroupID + "-" + roleID;

      // set basic data
      JSONObject subgroupJSON = new JSONObject();
      subgroupJSON.put(":name", subGroupID);
      subgroupJSON.put("sakai:parent-group-id", mainGroupID);
      subgroupJSON.put("sakai:parent-group-title", this.data.getString(WorldCreationServlet.PARAMS.title.toString()));
      subgroupJSON.put("sakai:role-title", role.getString(WorldCreationServlet.PARAMS.title.toString()));
      subgroupJSON.put("sakai:role-title-plural", role.getString(WorldCreationServlet.PARAMS.titlePlural.toString()));
      subgroupJSON.put("sakai:group-id", subGroupID);
      subgroupJSON.put("sakai:excludeSearch", true);
      subgroupJSON.put("sakai:pseudoGroup", true);
      subgroupJSON.put("sakai:pseudoGroup@TypeHint", "Boolean");

      // set acls
      subgroupJSON.put("sakai:group-visible", visibility);
      subgroupJSON.put("sakai:group-joinable", joinability);

      if ("members-only".equals(visibility)) {
        subgroupJSON.accumulate(":viewer", subGroupID);
        subgroupJSON.accumulate(":viewer@Delete", "everyone");
        subgroupJSON.accumulate(":viewer@Delete", "anonymous");
        this.mainGroupUpdateData.accumulate(":viewer", subGroupID);
        this.mainGroupUpdateData.accumulate(":viewer@Delete", "everyone");
        this.mainGroupUpdateData.accumulate(":viewer@Delete", "anonymous");
      } else if ("logged-in-only".equals(visibility)) {
        subgroupJSON.accumulate(":viewer", "everyone");
        subgroupJSON.accumulate(":viewer@Delete", "anonymous");
        this.mainGroupUpdateData.accumulate(":viewer", "everyone");
        this.mainGroupUpdateData.accumulate(":viewer@Delete", "anonymous");
      } else {
        subgroupJSON.accumulate(":viewer", "everyone");
        subgroupJSON.accumulate(":viewer", "anonymous");
        this.mainGroupUpdateData.accumulate(":viewer", "everyone");
        this.mainGroupUpdateData.accumulate(":viewer", "anonymous");
      }

      this.subgroups.put(roleID, subgroupJSON);
      this.mainGroupUpdateData.accumulate(":member", subGroupID);
      this.mainGroupUpdateData.accumulate(":viewer", subGroupID);

    }
  }

  private void createManagerRelationships(JSONArray roles) throws JSONException {
    for (int i = 0; i < roles.length(); i++) {
      JSONObject role = new JSONObject(roles.getString(i)); // roles comes to us as an array of strings because of Sling quirk
      Boolean isManagerRole = role.getBoolean(WorldCreationServlet.PARAMS.isManagerRole.toString());
      if (isManagerRole) {
        String mainGroupID = data.getString(WorldCreationServlet.PARAMS.id.toString());
        String roleID = role.getString(WorldCreationServlet.PARAMS.id.toString());
        String subGroupID = mainGroupID + "-" + roleID;
        JSONObject thisSubGroup = this.subgroups.get(roleID);

        // management role manages one or more other subgroups ...
        JSONArray rolesManaged = role.getJSONArray(WorldCreationServlet.PARAMS.manages.toString());
        for (int j = 0; j < rolesManaged.length(); j++) {
          String managedRoleID = rolesManaged.getString(j);
          JSONObject managedSubgroup = this.subgroups.get(managedRoleID);
          managedSubgroup.accumulate(":manager", subGroupID);
        }

        // ... management role manages itself
        thisSubGroup.accumulate(":manager", subGroupID);

        // ... management role also manages the main group
        this.mainGroupUpdateData.accumulate(":manager", subGroupID);

      }
    }
  }

  private void createMemberships() throws JSONException {
    JSONArray users;
    try {
      users = this.data.getJSONArray(WorldCreationServlet.PARAMS.usersToAdd.toString());
    } catch (JSONException ignored) {
      // no users, so return
      return;
    }
    for (int i = 0; i < users.length(); i++) {
      JSONObject thisUser = users.getJSONObject(i);
      String userID = thisUser.getString("userid");
      String roleID = thisUser.getString("role");
      JSONObject subgroup = this.subgroups.get(roleID);
      subgroup.accumulate(":member", userID);
      subgroup.accumulate(":viewer", userID);
    }
  }

  private void createSubgroups() throws JSONException, URISyntaxException, IOException, ServletException {
    // do the actual subgroup creation
    for (JSONObject subgroup : this.subgroups.values()) {
      LOGGER.debug("Creating subgroup " + subgroup.getString(":name") + "; data = " + subgroup.toString(2));
      SubRequest subgroupRequest = new SubRequest("/system/userManager/group.create.json", "POST",
              subgroup, request, response, write);
      subgroupRequest.doForward();
    }
  }

  private void updateMainGroup() throws JSONException, IOException, URISyntaxException, ServletException {
    String mainGroupID = data.getString(WorldCreationServlet.PARAMS.id.toString());
    LOGGER.debug("Updating main group " + mainGroupID + "; data = " + this.mainGroupUpdateData.toString(2));
    SubRequest mainGroupUpdateRequest = new SubRequest("/system/userManager/group/" + mainGroupID + ".update.json",
            "POST", this.mainGroupUpdateData, request, response, write);
    mainGroupUpdateRequest.doForward();
  }
}

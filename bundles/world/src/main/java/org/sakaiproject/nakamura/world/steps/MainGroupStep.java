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

public class MainGroupStep extends AbstractWorldCreationStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainGroupStep.class);

  public MainGroupStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {
    JSONObject groupJSON = new JSONObject();
    String groupID = data.getString(WorldCreationServlet.PARAMS.id.toString());
    groupJSON.put(":name", groupID);
    groupJSON.put("sakai:group-title", data.getString(WorldCreationServlet.PARAMS.title.toString()));
    groupJSON.put("sakai:group-description", data.getString(WorldCreationServlet.PARAMS.description.toString()));
    groupJSON.put("sakai:group-id", groupID);

    groupJSON.put("sakai:category", this.worldTemplate.getString("worldTemplateCategory"));
    groupJSON.put("sakai:templateid", this.worldTemplate.getString("id"));
    groupJSON.put("sakai:joinRole", this.worldTemplate.getString("joinRole"));
    groupJSON.put("sakai:creatorRole", this.worldTemplate.getString("creatorRole"));
    // customStyle is an optional parameter to apply a style to the world
    if (this.worldTemplate.has("customStyle")) {
      groupJSON.put("sakai:customStyle", this.worldTemplate.getString("customStyle"));
    }
    try {
      // arrays of JSON come back out of Sling as arrays of strings, so we have to convert, bcause sakai:roles is
      // really supposed to be an array of JSON, stringified
      JSONArray roles = new JSONArray();
      JSONArray roleStringArray = this.worldTemplate.getJSONArray(WorldCreationServlet.PARAMS.roles.toString());
      for ( int i = 0; i < roleStringArray.length(); i++ ) {
        roles.put(new JSONObject(roleStringArray.getString(i)));
      }
      groupJSON.put("sakai:roles", roles.toString());
    } catch (JSONException ignored) {
      // that's ok, no roles
    }

    LOGGER.debug("Creating main group " + groupID + "; data = " + groupJSON.toString(2));
    SubRequest mainGroupRequest = new SubRequest("/system/userManager/group.create.json", "POST",
            groupJSON, request, response, write);
    mainGroupRequest.doForward();
  }

}

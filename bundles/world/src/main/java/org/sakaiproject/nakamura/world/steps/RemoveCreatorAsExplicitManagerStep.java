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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;

public class RemoveCreatorAsExplicitManagerStep extends AbstractWorldCreationStep {

  public RemoveCreatorAsExplicitManagerStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {
    removeCreatorAsExplicitManager();
  }

  private void removeCreatorAsExplicitManager() throws JSONException, IOException, ServletException, URISyntaxException {
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    String creatorID = jcrSession.getUserID();

    // remove creator as mgr from main group and the subgroups
    for (String group : getGroups()) {
      SubRequest deleteRequest = new SubRequest("/system/userManager/group/" + group + ".update.json", "POST",
              new JSONObject().put(":manager@Delete", creatorID), request, response, write);
      deleteRequest.doForward();
    }
  }

  private List<String> getGroups() throws JSONException {
    List<String> groups = new ArrayList<String>();
    String mainGroupID = this.data.getString(WorldCreationServlet.PARAMS.id.toString());
    groups.add(mainGroupID);

    JSONArray roles;
    try {
      roles = this.worldTemplate.getJSONArray(WorldCreationServlet.PARAMS.roles.toString());
    } catch (JSONException ignored) {
      // no roles
      return groups;
    }

    for (int i = 0; i < roles.length(); i++) {
      JSONObject role = new JSONObject(roles.getString(i)); // roles comes to us as an array of strings because of Sling quirk
      String roleID = role.getString(WorldCreationServlet.PARAMS.id.toString());
      String subGroupID = mainGroupID + "-" + roleID;
      groups.add(subGroupID);
    }

    return groups;
  }
}

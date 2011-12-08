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
import javax.servlet.ServletException;

public class TagStep extends AbstractWorldCreationStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(TagStep.class);

  public TagStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {
    JSONArray tags;
    try {
      tags = this.data.getJSONArray(WorldCreationServlet.PARAMS.tags.toString());
    } catch (JSONException ignored) {
      // no tags, so bail out
      return;
    }

    for (int i = 0; i < tags.length(); i++) {
      String tagName = tags.getString(i);
      applyTagToGroupProfile(tagName);
    }
  }

  private void applyTagToGroupProfile(String tagName) throws JSONException, URISyntaxException, IOException, ServletException {
    String mainGroupID = data.getString(WorldCreationServlet.PARAMS.id.toString());
    JSONObject authProfileData = new JSONObject();
    authProfileData.put("key", "/tags/" + tagName);
    authProfileData.put(":operation", "tag");
    LOGGER.debug("Marking group " + mainGroupID + "'s authprofile with tag " + tagName + "; data = " + authProfileData.toString(2));
    SubRequest authProfileStep = new SubRequest("/~" + mainGroupID + "/public/authprofile", "POST",
            authProfileData, request, response, write);
    authProfileStep.doForward();
  }

}

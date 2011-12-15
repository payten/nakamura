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
import org.sakaiproject.nakamura.util.JSONUtils;
import org.sakaiproject.nakamura.world.SubRequest;
import org.sakaiproject.nakamura.world.WorldCreationServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.regex.Pattern;
import javax.servlet.ServletException;

public class DocStep extends AbstractWorldCreationStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocStep.class);

  private static final Pattern PATTERN_REFID = Pattern.compile("\\$\\{refid\\}");

  private static final Pattern PATTERN_GROUPID = Pattern.compile("\\$\\{groupid\\}");

  JSONArray pooledContentIDs = new JSONArray();

  public DocStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {
    substituteTokens();
    createPooledContent();
    addDocStructureToGroup();
  }

  private void substituteTokens() throws JSONException {
    String processedTemplate = PATTERN_REFID.matcher(this.worldTemplate.toString()).replaceAll(generateWidgetID());
    String mainGroupID = this.data.getString(WorldCreationServlet.PARAMS.id.toString());
    processedTemplate = PATTERN_GROUPID.matcher(processedTemplate).replaceAll(mainGroupID);
    this.worldTemplate = new JSONObject(processedTemplate);
  }

  private String generateWidgetID() {
    return "id" + Math.round(Math.random() * 10000000);
  }

  private void createPooledContent() throws JSONException, IOException, URISyntaxException, ServletException {
    JSONObject structure;
    try {
      structure = this.worldTemplate.getJSONObject(WorldCreationServlet.PARAMS.structure.toString());
    } catch (JSONException ignored) {
      // no structure, so bail out
      return;
    }

    // Grab the creatorID from the jcrSession
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    String creatorID = jcrSession.getUserID();

    Iterator<String> keys = structure.keys();
    while (keys.hasNext()) {
      JSONObject docDefinition;
      try {
        docDefinition = structure.getJSONObject(keys.next());
      } catch (JSONException ignored) {
        // value is not a json object
        continue;
      }
      String docRef = docDefinition.getString("_docref");
      JSONObject docContent = this.worldTemplate.getJSONObject("docs").getJSONObject(docRef);

      String visibility = this.data.getString(WorldCreationServlet.PARAMS.visibility.toString());
      String permission = "private";
      JSONArray viewers = docDefinition.getJSONArray("_view");
      JSONArray editors = docDefinition.getJSONArray("_edit");
      if (JSONUtils.arrayContains(viewers, "anonymous") && visibility.equals("public")) {
        permission = "public";
      } else if (JSONUtils.arrayContains(viewers, "everyone") && (visibility.equals("public") || visibility.equals("logged-in-only"))) {
        permission = "everyone";
      }

      JSONObject createFileData = new JSONObject();
      createFileData.put("sakai:pooled-content-file-name", docDefinition.getString("_title"));
      createFileData.put("sakai:description", "");
      createFileData.put("sakai:permissions", permission);
      createFileData.put("sakai:copyright", "creativecommons");
      createFileData.put("structure0", docContent.getJSONObject("structure0").toString());
      createFileData.put("mimeType", "x-sakai/document");
      if (docContent.has("excludeSearch")) {
        createFileData.put("sakai:excludeSearch", docContent.getBoolean("excludeSearch"));
      }

      LOGGER.debug("Creating pooled content, data = " + createFileData.toString(2));
      SubRequest pooledContentRequest = new SubRequest("/system/pool/createfile", "POST", createFileData, request, response, write);
      pooledContentRequest.doForward();

      // read response for the created content's pool id
      JSONObject responseJSON = new JSONObject(pooledContentRequest.getBody());
      String poolID = responseJSON.getJSONObject("_contentItem").getString("poolId");
      LOGGER.debug("Created pooled content item " + poolID);
      docDefinition.put("_pid", poolID);
      this.pooledContentIDs.put(poolID);

      // now fill in the actual doc content
      fillContent(docContent, poolID);

      // now set ACLs on the file
      setPermissions(permission, poolID, viewers, editors, creatorID);

    }

    this.write.object();
    this.write.key("pooledContentIDs");
    this.write.value(this.pooledContentIDs);
    this.write.endObject();

  }

  private void fillContent(JSONObject docContent, String poolID) throws JSONException, URISyntaxException, IOException, ServletException {
    docContent.remove("structure0");
    JSONObject fillContentData = new JSONObject();
    fillContentData.put(":operation", "import");
    fillContentData.put(":contentType", "json");
    fillContentData.put(":replace", true);
    fillContentData.put(":replaceProperties", true);
    fillContentData.put(":content", docContent.toString());

    LOGGER.debug("Filling in pooled content data = " + fillContentData.toString(2));
    SubRequest fillContentRequest = new SubRequest("/p/" + poolID, "POST", fillContentData, request, response, write);
    fillContentRequest.doForward();
  }

  private void setPermissions(String permission, String poolID, JSONArray viewers, JSONArray editors, String creatorUserID)
          throws JSONException, IOException, URISyntaxException, ServletException {
    // this logic duplicates the client-side code in sakai.api.content.setFilePermissions
    String path = "/p/" + poolID;
    String groupID = data.getString(WorldCreationServlet.PARAMS.id.toString());
    JSONObject membersData = new JSONObject();

    if (permission.equals("everyone")) {
      // everyone = all logged in users
      membersData.accumulate(":viewer", "everyone").accumulate(":viewer@Delete", "anonymous");
    } else if (permission.equals("public")) {
      // public = anonymous and logged-in
      membersData.accumulate(":viewer", "everyone").accumulate(":viewer", "anonymous");
    } else if (permission.equals("private")) {
      // managers and members only
      membersData.accumulate(":viewer@Delete", "everyone").accumulate(":viewer@Delete", "anonymous");
    } else if (permission.equals("group")) {
      // group members only
      membersData.accumulate(":viewer", groupID).accumulate(":viewer@Delete", "everyone").accumulate(":viewer@Delete", "anonymous");
    }

    // Always remove the creator as an explicit manager
    membersData.accumulate(":manager@Delete", creatorUserID);

    // set memberships for the group members and managers
    for (int i = 0; i < viewers.length(); i++) {
      String principal = viewers.getString(i);
      if (principal.startsWith("-")) {
        membersData.accumulate(":viewer", groupID + principal);
      }
    }
    for (int i = 0; i < editors.length(); i++) {
      String principal = editors.getString(i);
      if (principal.startsWith("-")) {
        membersData.accumulate(":manager", groupID + principal);
      }
    }
    setACL(path + ".members.html", membersData);

  }

  private void setACL(String path, JSONObject params) throws JSONException, URISyntaxException, IOException, ServletException {
    LOGGER.debug("Setting ACL on " + path + " with data = " + params.toString(2));
    SubRequest aclRequest = new SubRequest(path, "POST",
            params, request, response, write);
    aclRequest.doForward();
  }

  private void addDocStructureToGroup() throws JSONException, IOException, URISyntaxException, ServletException {
    JSONObject structure;
    try {
      structure = this.worldTemplate.getJSONObject(WorldCreationServlet.PARAMS.structure.toString());
    } catch (JSONException ignored) {
      // no structure, so bail out
      return;
    }

    Iterator<String> keys = structure.keys();
    while (keys.hasNext()) {
      JSONObject docDefinition;
      try {
        docDefinition = structure.getJSONObject(keys.next());
      } catch (JSONException ignored) {
        // value is not a json object
        continue;
      }

      String viewAsString = docDefinition.getJSONArray("_view").toString();
      String editAsString = docDefinition.getJSONArray("_edit").toString();
      docDefinition.put("_view", viewAsString);
      docDefinition.put("_edit", editAsString);
    }

    JSONObject structure0 = new JSONObject();
    structure0.put("structure0", structure.toString());
    JSONObject groupData = new JSONObject();
    groupData.put(":operation", "import");
    groupData.put(":contentType", "json");
    groupData.put(":replace", true);
    groupData.put(":replaceProperties", true);
    groupData.put(":content", structure0.toString());
    String groupID = data.getString(WorldCreationServlet.PARAMS.id.toString());

    LOGGER.debug("Adding docstructure to group " + groupID + " with data = " + groupData.toString(2));
    SubRequest groupRequest = new SubRequest("/~" + groupID + "/docstructure", "POST", groupData, request, response, write);
    groupRequest.doForward();
  }

}


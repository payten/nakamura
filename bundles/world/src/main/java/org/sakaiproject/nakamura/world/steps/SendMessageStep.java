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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import javax.servlet.ServletException;

public class SendMessageStep extends AbstractWorldCreationStep {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendMessageStep.class);

  private static final Pattern PATTERN_CREATOR = Pattern.compile("\\$\\{creatorName\\}");

  private static final Pattern PATTERN_ROLE = Pattern.compile("\\$\\{role\\}");

  private static final Pattern PATTERN_FIRSTNAME = Pattern.compile("\\$\\{firstName\\}");

  private static final Pattern PATTERN_GROUPNAME = Pattern.compile("\\$\\{groupName\\}");

  private static final Pattern PATTERN_LINK = Pattern.compile("\\$\\{link\\}");

  private static final String MESSAGE_MODE = "messageMode";

  public SendMessageStep(JSONObject data, JSONObject worldTemplate, SlingHttpServletRequest request, SlingHttpServletResponse response, JSONWriter write) {
    super(data, worldTemplate, request, response, write);
  }

  @Override
  public void handle() throws Exception {
    JSONObject message;
    try {
      message = this.data.getJSONObject("message");
    } catch (JSONException ignored) {
      // no message, so bail out
      return;
    }

    sendMessage(message);
  }

  private void sendMessage(JSONObject message) throws JSONException, IOException, ServletException, URISyntaxException {
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    String creatorID = jcrSession.getUserID();

    JSONArray toSend = message.getJSONArray("toSend");
    for (int i = 0; i < toSend.length(); i++) {
      JSONObject recipient = toSend.getJSONObject(i);
      JSONObject messageJSON = new JSONObject();
      String body = replaceTokens(message.getString("body"), message, recipient);
      messageJSON.put("sakai:type", "internal");
      messageJSON.put("sakai:sendstate", "pending");
      messageJSON.put("sakai:messagebox", "outbox");
      messageJSON.put("sakai:to", "internal:" + recipient.getString("userid"));
      messageJSON.put("sakai:from", creatorID);
      messageJSON.put("sakai:subject", replaceTokens(message.getString("subject"), message, recipient));
      messageJSON.put("sakai:body", body);
      messageJSON.put("sakai:category", "message");

      if (wantsInternalMessage(recipient)) {
        LOGGER.debug("Sending group creation internal message with data " + messageJSON.toString(2));
        SubRequest internalMessageRequest = new SubRequest("/~" + creatorID + "/message.create.html", "POST",
                messageJSON, request, response, write);
        internalMessageRequest.doForward();
      }

      if (wantsExternalMessage(recipient)) {
        // send an smtp aka external message
        messageJSON.put("sakai:type", "smtp");
        messageJSON.put("sakai:messagebox", "pending");
        messageJSON.put("sakai:templatePath", "/var/templates/email/group_invitation");
        messageJSON.put("sakai:templateParams",
                "sender=" + message.getString("creatorName")
                        + "|system=" + message.getString("system")
                        + "|name=" + message.getString("groupName")
                        + "|body=" + body
                        + "|link=" + message.getString("link"));

        LOGGER.debug("Sending group creation smtp message with data " + messageJSON.toString(2));
        SubRequest smtpMessageRequest = new SubRequest("/~" + creatorID + "/message.create.html", "POST",
                messageJSON, request, response, write);
        smtpMessageRequest.doForward();
      }

    }

  }

  private String replaceTokens(String input, JSONObject message, JSONObject recipient) throws JSONException {
    String s = PATTERN_CREATOR.matcher(input).replaceAll(message.getString("creatorName"));
    s = PATTERN_GROUPNAME.matcher(s).replaceAll(message.getString("groupName"));
    s = PATTERN_LINK.matcher(s).replaceAll(message.getString("link"));
    s = PATTERN_ROLE.matcher(s).replaceAll(recipient.getString("role"));
    s = PATTERN_FIRSTNAME.matcher(s).replaceAll(recipient.getString("firstName"));
    return s;
  }

  private boolean wantsInternalMessage(JSONObject recipient) throws JSONException {
    return recipient.getString(MESSAGE_MODE).equals("internal") || recipient.getString(MESSAGE_MODE).equals("both");
  }

  private boolean wantsExternalMessage(JSONObject recipient) throws JSONException {
    return recipient.getString(MESSAGE_MODE).equals("external") || recipient.getString(MESSAGE_MODE).equals("both");
  }

}


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

package org.sakaiproject.nakamura.world;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.json.jcr.JsonItemWriter;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.util.JSONUtils;
import org.sakaiproject.nakamura.util.PathUtils;
import org.sakaiproject.nakamura.util.StringUtils;
import org.sakaiproject.nakamura.world.steps.AbstractWorldCreationStep;
import org.sakaiproject.nakamura.world.steps.DocStep;
import org.sakaiproject.nakamura.world.steps.MainGroupStep;
import org.sakaiproject.nakamura.world.steps.RemoveCreatorAsExplicitManagerStep;
import org.sakaiproject.nakamura.world.steps.RoleStep;
import org.sakaiproject.nakamura.world.steps.SendMessageStep;
import org.sakaiproject.nakamura.world.steps.TagStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@Component(immediate = true, metatype = true, enabled = true)
@SlingServlet(paths = {"/system/world/create"}, methods = {"POST"}, generateComponent = false)
public class WorldCreationServlet extends SlingAllMethodsServlet {

  public static enum PARAMS {
    data,
    id,
    title,
    titlePlural,
    tags,
    description,
    worldTemplate,
    roles,
    isManagerRole,
    manages,
    usersToAdd,
    visibility,
    joinability,
    structure
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(WorldCreationServlet.class);

  @Reference
  protected transient Repository repository;

  @Reference
  protected transient SlingRepository slingRepository;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {

    String dataParam = request.getParameter(PARAMS.data.toString());
    if (dataParam == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "'data' parameter is required");
      return;
    }

    try {
      JSONObject data = new JSONObject(dataParam);
      LOGGER.debug("Data JSON = " + data.toString(2));

      StringWriter sw = new StringWriter();
      JSONWriter write = new JSONWriter(sw);
      write.object();
      write.key("results");
      write.array();

      // validate world data before creating
      String errorMessage = getValidationMessage(data);
      if (errorMessage != null) {
        // world create data not valid
        write.object();
        write.key("error");
        write.value(errorMessage);
        write.endObject();
        write.object();
        write.key("created");
        write.value(false);
        write.endObject();
      } else {
        // validated successfully, go ahead and run thru all the steps
        List<AbstractWorldCreationStep> steps = getSteps(request, response, data, write);
        for (AbstractWorldCreationStep step : steps) {
          step.handle();
        }
        write.object();
        write.key("created");
        write.value(true);
        write.endObject();
      }

      write.endArray();
      write.endObject();
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(sw.getBuffer().toString());


    } catch (JSONException e) {
      LOGGER.error("Got a JSONException reading world data", e);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse json for world");
    } catch (URISyntaxException e) {
      LOGGER.error("Should never happen since we construct URIs ourselves");
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (AccessDeniedException e) {
      LOGGER.error("AccessDeniedException checking whether group exists", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (StorageClientException e) {
      LOGGER.error("StorageClientException checking whether group exists", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (RepositoryException e) {
      LOGGER.error("RepositoryException from Sling looking up world template", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (Exception e) {
      LOGGER.error("Unhandled exception ", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

  }
  
  private String getValidationMessage(JSONObject data) throws JSONException, StorageClientException, AccessDeniedException {
    String groupId = data.getString(PARAMS.id.toString());
    if (groupExists(groupId)) {
      return "Group already exists";
    }

    if (invalidGroupId(groupId)) {
      return "Invalid group id";
    }

    return null;
  }

  private boolean invalidGroupId(String groupId) {
    return !StringUtils.containsOnlySafeChars(groupId);
  }

  private boolean groupExists(String groupID) throws StorageClientException, AccessDeniedException {
    Session session = null;
    Authorizable authz = null;
    try {
      session = repository.loginAdministrative();
      authz = session.getAuthorizableManager().findAuthorizable(groupID);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
    return authz != null;
  }

  private List<AbstractWorldCreationStep> getSteps(SlingHttpServletRequest request, SlingHttpServletResponse response, JSONObject data, JSONWriter write) throws RepositoryException, JSONException {
    JSONObject worldTemplate = getWorldTemplate(data);
    List<AbstractWorldCreationStep> steps = new ArrayList<AbstractWorldCreationStep>();
    steps.add(new MainGroupStep(data, worldTemplate, request, response, write));
    steps.add(new TagStep(data, worldTemplate, request, response, write));
    steps.add(new RoleStep(data, worldTemplate, request, response, write));
    steps.add(new DocStep(data, worldTemplate, request, response, write));
    steps.add(new SendMessageStep(data, worldTemplate, request, response, write));
    steps.add(new RemoveCreatorAsExplicitManagerStep(data, worldTemplate, request, response, write));
    return steps;
  }

  private JSONObject getWorldTemplate(JSONObject data) throws JSONException, RepositoryException {
    String path = data.getString(PARAMS.worldTemplate.toString());

    javax.jcr.Session session = null;
    JSONObject worldTemplate = new JSONObject();
    try {
      session = this.slingRepository.loginAdministrative(null);
      Node node = session.getNode(path);

      // slightly circuituous way to convert the JCR node into a JSON object, recursing through all subnodes.
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintWriter printWriter = new PrintWriter(baos);
      JsonItemWriter jsonWriter = new JsonItemWriter(new HashSet<String>(0));
      jsonWriter.dump(node, printWriter, Integer.MAX_VALUE, false);
      printWriter.flush();
      String json = baos.toString("utf-8");
      worldTemplate = new JSONObject(json);

      JSONUtils.stripJCRNodes(worldTemplate);

      String parentPath = PathUtils.getParentReference(path);
      String templateCategory = parentPath.substring(parentPath.lastIndexOf("/") + 1, parentPath.length());
      worldTemplate.put("worldTemplateCategory", templateCategory);

    } catch (IOException e) {
      LOGGER.error("Got mystery IO exception reading world template", e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
    LOGGER.debug("World template dump: " + worldTemplate.toString(2));
    return worldTemplate;
  }

}

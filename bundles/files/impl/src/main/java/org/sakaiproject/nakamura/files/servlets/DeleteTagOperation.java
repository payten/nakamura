/**
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
package org.sakaiproject.nakamura.files.servlets;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_PROFILE_RESOURCE_TYPE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_PROFILE_RESOURCE_TYPE;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.files.TagUtils;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "DeleteTagOperation", okForVersion = "1.1",
  shortDescription = "Delete a tag from node", description = { "Delete a tag from a content node." }, methods = { @ServiceMethod(name = "POST", description = { "This operation should be performed on the node you wish to tag. Tagging on any item will be performed by adding a weak reference to the content item. Put simply a sakai:tag-uuid property with the UUID of the tag node. We use the UUID to uniquely identify the tag in question, a string of the tag name is not sufficient. This allows the tag to be renamed and moved without breaking the relationship. Additionally for convenience purposes we may put the name of the tag at the time of tagging in sakai:tag although this will not be actively maintained. " }, parameters = {
    @ServiceParameter(name = ":operation", description = "The value HAS TO BE <i>tag</i>."),
    @ServiceParameter(name = "key", description = "Can be either 1) A fully qualified path, 2) UUID, or 3) a content poolId.") }, response = {
    @ServiceResponse(code = 201, description = "The tag was added to the content node."),
    @ServiceResponse(code = 400, description = "The request did not have sufficient information to perform the tagging, probably a missing parameter or the uuid does not point to an existing tag."),
    @ServiceResponse(code = 403, description = "Anonymous users can't tag anything, other people can tag <i>every</i> node in the repository where they have READ on."),
    @ServiceResponse(code = HttpServletResponse.SC_NOT_FOUND, description = "Requested Node  for given key could not be found."),
    @ServiceResponse(code = 500, description = "Something went wrong, the error is in the HTML.") }) }, bindings = { @ServiceBinding(type = BindingType.OPERATION, bindings = { "tag" }) })
@Component(immediate = true)
@Service(value = SparsePostOperation.class)
@Properties(value = {
    @Property(name = "sling.post.operation", value = "deletetag"),
    @Property(name = "service.description", value = "Creates an internal link to a file."),
    @Property(name = "service.vendor", value = "The Sakai Foundation") })
public class DeleteTagOperation extends AbstractSparsePostOperation {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteTagOperation.class);

  @Reference
  private Repository repository;

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.servlets.post.AbstractSlingPostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.servlets.HtmlResponse, java.util.List)
   */
  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath) throws StorageClientException, AccessDeniedException {

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));

    // Check if the user has the required minimum privilege.
    String user = request.getRemoteUser();
    if (UserConstants.ANON_USERID.equals(user)) {
      LOGGER.warn ("Anonymous user denied ability to delete tag.");
      response.setStatus(HttpServletResponse.SC_FORBIDDEN,
          "Anonymous users can't delete tags.");
      return;
    }

    // Check if the uuid is in the request.
    String key = request.getParameter("key");
    if (StringUtils.isBlank(key)) {
      LOGGER.warn ("attempt to delete tag without key");
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "Missing parameter: key");
      return;
    }

    Content content = request.getResource().adaptTo(Content.class);
    if (content == null) {
      LOGGER.warn ("attempt to delete a tag on a null resource");
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "A tag operation must be performed on an actual resource");
      return;
    }

    // Grab the tagNode.
    Content tag = contentManager.get(key);
    if (tag == null) {
      LOGGER.warn ("attempted to delete tag {} which does not exist", key);
      response.setStatus(HttpServletResponse.SC_NOT_FOUND, "Provided key not found.");
      return;
    }
    if (!TagUtils.isTag(tag)) {
      LOGGER.warn ("{} is not a tag and so cannot be deleted via DeleteTagOperation", key);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "Provided key doesn't point to a tag.");
      return;
    }

    // make sure we're trying to delete a tag that exists on the content
    String tagName = (String) tag.getProperty(SAKAI_TAG_NAME);
    String[] existingTags = (String[]) content.getProperty(SAKAI_TAGS);
    boolean tagged = false;

    for (String existingTag : existingTags) {
      if (existingTag.equals(tagName)) {
        tagged = true;
        break;
      }
    }

    if (tagged) {
      LOGGER.debug ("deleting tag {} from {}", new String[] {tagName, content.getPath()});
      TagUtils.deleteTag(contentManager, content, tagName);
      // keep authz in sync with authprofile
      final AuthorizableManager authManager = session.getAuthorizableManager();
      final String resourceType = (String) content.getProperty(SLING_RESOURCE_TYPE_PROPERTY);
      final boolean isProfile = USER_PROFILE_RESOURCE_TYPE.equals(resourceType)
              || GROUP_PROFILE_RESOURCE_TYPE.equals(resourceType);
      // If we're remove a tag on an authprofile, remove the property here
      if (isProfile) {
        final String azId = PathUtils.getAuthorizableId(content.getPath());
        final Authorizable authorizable = authManager.findAuthorizable(azId);
        if (authorizable != null) {

          final Set<String> nameSet = Sets
              .newHashSet(StorageClientUtils.nonNullStringArray((String[]) authorizable
                  .getProperty(SAKAI_TAGS)));
          nameSet.remove(tagName);
          authorizable.setProperty(SAKAI_TAGS,
              nameSet.toArray(new String[nameSet.size()]));

          authManager.updateAuthorizable(authorizable);
        }
      } else {
        Session adminSession = null;
        try {
          adminSession = repository.loginAdministrative();
          ContentManager cm = adminSession.getContentManager();
          Content adminTag = cm.get(key);
          String[] tagNames = StorageClientUtils.nonNullStringArray((String[]) content
              .getProperty(SAKAI_TAGS));
          TagUtils.bumpTagCounts(adminTag, tagNames, false, false, cm);
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
    }
  }
}

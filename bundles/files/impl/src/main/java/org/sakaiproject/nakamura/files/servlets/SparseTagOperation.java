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
import static org.sakaiproject.nakamura.api.files.FilesConstants.RT_SAKAI_TAG;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.TOPIC_FILES_TAG;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_PROFILE_RESOURCE_TYPE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_PROFILE_RESOURCE_TYPE;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.Modification;
import org.osgi.service.event.EventAdmin;
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
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.PathUtils;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

@Component(immediate = true)
@Service(value = SparsePostOperation.class)
@Properties(value = {
  @Property(name = "sling.post.operation", value = "tag"),
  @Property(name = "service.description", value = "Associates one or more tags with a piece of content."),
  @Property(name = "service.vendor", value = "The Sakai Foundation") })
@ServiceDocumentation(name = "SparseTagOperation", okForVersion = "1.2",
  shortDescription = "Tag a node",
  description = "Add a tag to a node.",
  bindings = {
    @ServiceBinding(type = BindingType.OPERATION, bindings = { "tag" })
  },
  methods = {
    @ServiceMethod(name = "POST",
      description = { "This operation should be performed on the node you wish to tag. Tagging on any item will be performed by adding a weak reference to the content item. Put simply a sakai:tag-uuid property with the UUID of the tag node. We use the UUID to uniquely identify the tag in question, a string of the tag name is not sufficient. This allows the tag to be renamed and moved without breaking the relationship. Additionally for convenience purposes we may put the name of the tag at the time of tagging in sakai:tag although this will not be actively maintained. "
      },
      parameters = {
        @ServiceParameter(name = ":operation", description = "(required) The value HAS TO BE <i>tag</i>."),
        @ServiceParameter(name = "key", description = "(required, multiple) Can be either 1) A fully qualified path, 2) UUID, or 3) a content poolId. Accepts single or multiple values on this key.")
      },
      response = {
        @ServiceResponse(code = 201, description = "The tag was added to the content node."),
        @ServiceResponse(code = 400, description = "Bad request: either the 'key' parameter was missing, or the resource to be tagged could not be found."),
        @ServiceResponse(code = 403, description = "Anonymous users can't tag anything, other people can tag <i>every</i> node in the repository where they have READ on."),
        @ServiceResponse(code = HttpServletResponse.SC_NOT_FOUND, description = "Requested Node  for given key could not be found."),
        @ServiceResponse(code = 500, description = "Something went wrong, the error is in the HTML.")
      })
  })
public class SparseTagOperation extends AbstractSparsePostOperation {

  @Reference
  protected transient EventAdmin eventAdmin;

  // even though this reference is never used,
  // keeping it here fixes KERN-2529
  @Reference
  private Repository repository;

  private static final Logger LOGGER = LoggerFactory.getLogger(SparseTagOperation.class);

  private static final String TAGS_BASE = "/tags/";

  /**
   * We assume that a tag should be created the first time it is used. So if the tag does
   * not exist this method creates the tag. ACLs are set to allow CAN_READ by all
   * identified users, and ALL by admins
   * 
   * @param resolver
   * @param tagContentPath
   * @return
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  protected Content getOrCreateTag(ResourceResolver resolver, String tagContentPath)
      throws AccessDeniedException, StorageClientException {
    // all tags live under /tags. Make sure the requester is trying to use a tag from
    // there, does not provide an empty path and the path has more than the base path
    if (tagContentPath == null || tagContentPath.length() <= TAGS_BASE.length()
        || !tagContentPath.startsWith(TAGS_BASE)) {
      return null;
    }

    Resource tagResource = resolver.getResource(tagContentPath);
    Content tagContent = null;

    if (tagResource == null) {
      Session session = StorageClientUtils.adaptToSession(resolver
          .adaptTo(javax.jcr.Session.class));
      Session adminSession = session.getRepository().loginAdministrative();

      // wrap in a try so we can ensure logout in finally
      try {
        tagContent = createTag(tagContentPath, adminSession);
      } catch (Exception e) {
        LOGGER.error("error creating tag {}", tagContentPath, e);
      } finally {
        if (adminSession != null) {
          try {
            adminSession.logout();
          } catch (Exception e) {// noop - this was just a failsafe}
          }
        }
      }
    } else {
      LOGGER.info("retrieved existing tag at {}", tagContentPath);
      Content _tagContent = tagResource.adaptTo(Content.class);
      if (TagUtils.isTag(_tagContent)) {
        tagContent = _tagContent;
      }
    }

    return tagContent;
  }

  private Content createTag(String tagContentPath, Session adminSession) throws StorageClientException, AccessDeniedException {

    // make sure parent is also a tag, or the special /tags, or the special /tags/directory
    String parentPath = PathUtils.getParentReference(tagContentPath);
    Content parent = adminSession.getContentManager().get(parentPath);
    if ( parent == null && parentPath.startsWith(TAGS_BASE) ) {
      createTag(parentPath, adminSession);
    }

    LOGGER.info("tag {} is being created", tagContentPath);
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    String tag = tagContentPath.substring("/tags/".length());

    // create tag
    builder.put(SAKAI_TAG_NAME, tag);
    builder.put(SLING_RESOURCE_TYPE_PROPERTY, RT_SAKAI_TAG);

    Content tagContent = new Content(tagContentPath, builder.build());

    adminSession.getContentManager().update(tagContent);

    // set ACLs
    adminSession.getAccessControlManager()
        .setAcl(
            Security.ZONE_CONTENT,
            tagContentPath,
            new AclModification[] {
                new AclModification(AclModification.grantKey(User.ANON_USER),
                    Permissions.CAN_READ.getPermission(),
                    AclModification.Operation.OP_REPLACE),
                new AclModification(AclModification.grantKey(Group.EVERYONE),
                    Permissions.CAN_READ.getPermission(),
                    AclModification.Operation.OP_REPLACE),
                new AclModification(AclModification
                    .grantKey(Group.ADMINISTRATORS_GROUP), Permissions.ALL
                    .getPermission(), AclModification.Operation.OP_REPLACE) });
    return tagContent;
  }

  /**
   * {@inheritDoc}
   * 
   * @throws AccessDeniedException
   * @throws StorageClientException
   * 
   * @see org.apache.sling.servlets.post.AbstractSlingPostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.servlets.HtmlResponse, java.util.List)
   */
  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException {

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver()
        .adaptTo(javax.jcr.Session.class));

    // Check if the user has the required minimum privilege.
    String user = request.getRemoteUser();
    if (UserConstants.ANON_USERID.equals(user)) {
      LOGGER.warn("anonymous user attempted to tag an item");
      response.setStatus(HttpServletResponse.SC_FORBIDDEN,
          "Anonymous users can't tag things.");
      return;
    }

    Resource resource = request.getResource();
    Content content = resource.adaptTo(Content.class);
    ResourceResolver resourceResolver = request.getResourceResolver();

    if (content == null) {
      LOGGER.info("Missing Resource  {} ", resource.getPath());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "A tag operation must be performed on an actual resource");
      return;
    }

    // Check if the uuid is in the request.
    String[] keys = request.getParameterValues("key");
    if (keys == null || keys.length == 0) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter: key");
      return;
    }

    // put the keys into a set to filter unique keys
    Set<String> keysSet = Sets.newHashSet(keys);
    List<Content> tagResources = checkTags(response, resourceResolver, keysSet);
    if (tagResources.size() != keysSet.size()) {
      // stop processing if there weren't as many found resources as keys requested
      return;
    }

    // add all tags to the requested content node
    List<Content> addedTags = TagUtils.addTags(contentManager, content, tagResources);

    updateAuthorizable(session, content, tagResources, addedTags);

    updateCounts(request, session, user, content, addedTags);
  }

  /**
   * Check the requested tag names to make sure each is an existing sakai/tag node in
   * storage or can be created.
   *
   * @param response
   * @param resourceResolver
   * @param keys
   * @return
   */
  private List<Content> checkTags(HtmlResponse response,
      ResourceResolver resourceResolver, Set<String> keys) {
    List<Content> tagResources = Lists.newArrayList();
    List<String> badKeys = Lists.newArrayList();
    // check each tag before processing any.
    for (String key : keys) {
      try {
        Content tagResource = getOrCreateTag(resourceResolver, key);
        if (!TagUtils.isTag(tagResource)) {
          // filter out nodes that are really tags
          badKeys.add(key);
        } else {
          tagResources.add(tagResource);
        }
      } catch (AccessDeniedException e) {
        badKeys.add(key);
      } catch (StorageClientException e) {
        badKeys.add(key);
      }
    }

    // stop processing if any bad keys were provided
    if (badKeys.size() > 0) {
      // there were failures. send errors back to response and tag nothing
      response.setStatus(HttpServletResponse.SC_NOT_FOUND, "No tag exists at path(s): "
          + Joiner.on(',').join(badKeys));
    }
    return tagResources;
  }

  /**
   * Update the authorizable if we tagged a profile.
   *
   * @param authManager
   * @param content
   * @param tagResources
   * @param resourceType
   * @param addedTags
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private void updateAuthorizable(Session session, Content content,
      List<Content> tagResources, List<Content> addedTags) throws AccessDeniedException, StorageClientException {
    String resourceType = String.valueOf(content.getProperty(SLING_RESOURCE_TYPE_PROPERTY));
    boolean isProfile = USER_PROFILE_RESOURCE_TYPE.equals(resourceType)
        || GROUP_PROFILE_RESOURCE_TYPE.equals(resourceType);
    // update the authorizable if necessary
    if (isProfile) {
      AuthorizableManager authManager = session.getAuthorizableManager();

      String azId = PathUtils.getAuthorizableId(content.getPath());
      Authorizable authorizable = authManager.findAuthorizable(azId);
      if (authorizable != null) {
        Set<String> tagNameSet = Sets.newHashSet(PropertiesUtil
            .toStringArray(authorizable.getProperty(SAKAI_TAGS), new String[0]));
        boolean updated = false;

        for (Content tagResource : tagResources) {
          String tagName = String.valueOf(tagResource.getProperty(SAKAI_TAG_NAME));

          // If we're tagging an authorizable, add the property here
          if (!tagNameSet.contains(tagName)) {
            addedTags.add(tagResource);

            tagNameSet.add(tagName);
            updated = true;
          }
        }

        if (updated) {
          authorizable.setProperty(SAKAI_TAGS,
              tagNameSet.toArray(new String[tagNameSet.size()]));
          authManager.updateAuthorizable(authorizable);
        }
      }
    }
  }

  /**
   * Update tag counts and send events for tags that were used.
   *
   * @param request
   * @param session
   * @param user
   * @param content
   * @param addedTags
   * @throws ClientPoolException
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private void updateCounts(SlingHttpServletRequest request, Session session,
      String user, Content content, List<Content> addedTags) throws ClientPoolException,
      StorageClientException, AccessDeniedException {
    Session adminSession = null;
    try {
      adminSession = session.getRepository().loginAdministrative();
      ContentManager cm = adminSession.getContentManager();
      for (Content addedTag : addedTags) {
        Content adminTag = cm.get(addedTag.getPath());
        String[] tagNames = PropertiesUtil.toStringArray(content.getProperty(SAKAI_TAGS));
        TagUtils.bumpTagCounts(adminTag, tagNames, true, false, cm);

        // Send an OSGi event.
        String tagName = String.valueOf(addedTag.getProperty(SAKAI_TAG_NAME));
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(UserConstants.EVENT_PROP_USERID, user);
        properties.put("tag-name", tagName);
        EventUtils.sendOsgiEvent(request.getResource(), properties, TOPIC_FILES_TAG,
            eventAdmin);
      }
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

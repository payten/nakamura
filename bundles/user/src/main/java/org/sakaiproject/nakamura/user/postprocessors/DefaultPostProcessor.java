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
package org.sakaiproject.nakamura.user.postprocessors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.activity.ActivityUtils;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * 
 * <pre>
 * HomePostProcessor
 *  It creates the following if they dont exist (or onCreate)
 *  a:userID
 *      - sling:resourceType = sakai/user-home | sakai/group-home
 *      - sakai:search-exclude-tree = copied from authorizable if present on creation only
 *      + permissions: if anon: anon:read + everyone:read
 *                     if visibility public anon:read everyone:read
 *                     if visibility logged in anong:denyall everyone:read
 *                     if visibility private anon:denyall everyone:denyall
 *                     all principals in the managers property allowed all
 *                     all principals in the viewers property allows read
 *                     current user allowed all
 *  a:userID/public
 *  a:userID/private
 *      + permissions: everyone and anon denied read
 * 
 *  If they do exist (on everything else except delete)
 *  a:userID
 *     Change permissions to match visibility
 *     Change permissions to match managers and viewers
 * 
 * 
 * Sakai Group Postprocessor
 * sets the path property in the authorizable
 * sets a group-manages property name to the name of an auto generated group
 * generates that group (does not trigger any post processors)
 * sets properties in the manger group
 * adds members and removes members according to the request properties sakai:manager and sakai:manager@Delete
 * 
 * Sakau User Post processor
 * sets the path property in the authorizable
 * 
 * 
 * Message Post Processor
 * a:userID/message
 *    - sling:resourceType = sakai/messagestore
 *    + permissions: user can all
 *                   anon deny all
 *                   everyone deny all
 * 
 * Calendar
 * a:userID/calendar
 *     - sling:resourceType = sakai/calendar
 *     _ stores a default calendar (empty with no properties)
 *     + grants userID all
 * 
 * Connections
 * a:userID/contacts
 *     - sling:resourceType = sakai/contactstore
 *     + deny all for anon and everyone
 *       grants user all, except anon
 *     + creates a private group of viewers that only the current user can view (could be delayed as not used then)
 *
 * Profile post Processor
 * a:userId/profile
 *     - sling:resourceType = sakai/group-profile | sakai/user-profile
 *      Copies a template of content posted by the UI to generate a tree of content after processing, uses the ContentLoader to achieve this.
 *      Copies all the Authorizable Properties onto the authorizable node.
 * 
 * 
 * 
 * -----------------
 * 
 * We can hard code everything other than the profile importer in a single class
 * IMO the manager group is superfluous on a user and adds unecessary expense
 * </pre>
 */
@Component(immediate = true, metatype = true)
@Service(value = LiteAuthorizablePostProcessor.class)
@Properties(value = { @Property(name = "default", value = "true") })
public class DefaultPostProcessor implements LiteAuthorizablePostProcessor {

  private static final String CONTACTS_FOLDER = "/contacts";

  private static final String CALENDAR_FOLDER = "/calendar";

  private static final String MESSAGE_FOLDER = "/message";

  private static final String PROFILE_FOLDER = "/authprofile";

  private static final String PROFILE_BASIC = "/basic/elements";

  private static final String SAKAI_CONTACTSTORE_RT = "sparse/contactstore";

  private static final String SAKAI_CALENDAR_RT = "sakai/calendar";

  private static final String SAKAI_MESSAGESTORE_RT = "sakai/messagestore";

  private static final String SAKAI_PRIVATE_RT = "sakai/private";

  private static final String SAKAI_PUBLIC_RT = "sakai/public";

  private static final String SAKAI_SEARCH_EXCLUDE_TREE_PROP = "sakai:search-exclude-tree";

  private static final String SAKAI_USER_HOME_RT = "sakai/user-home";

  private static final String SAKAI_GROUP_HOME_RT = "sakai/group-home";

  private static final String SAKAI_GROUP_PROFILE_RT = "sakai/group-profile";

  private static final String SAKAI_USER_PROFILE_RT = "sakai/user-profile";

  private static final String SLING_RESOURCE_TYPE = "sling:resourceType";
  public static final String VISIBILITY_PRIVATE = "private";
  public static final String VISIBILITY_LOGGED_IN = "logged_in";
  public static final String VISIBILITY_PUBLIC = "public";

  public static final String PROFILE_JSON_IMPORT_PARAMETER = ":sakai:profile-import";

  public static final String PARAM_ADD_TO_MANAGERS_GROUP = ":sakai:manager";
  public static final String PARAM_REMOVE_FROM_MANAGERS_GROUP = PARAM_ADD_TO_MANAGERS_GROUP
      + SlingPostConstants.SUFFIX_DELETE;

  /**
   * Restrict behavior as closely as possible to Sling's original client-server Authorizable
   * API, avoiding action on OAE Home folders and related functionality.
   */
  public static final String PARAM_AUTHORIZABLE_ONLY = ":sakai:authorizableOnly";
  
  static final String VISIBILITY_PREFERENCE_DEFAULT = VISIBILITY_PUBLIC;
  @Property(value = VISIBILITY_PREFERENCE_DEFAULT, options = {
      @PropertyOption(name = VISIBILITY_PRIVATE, value = "The home is private."),
      @PropertyOption(name = VISIBILITY_LOGGED_IN, value = "The home is blocked to anonymous users; all logged-in users can see it."),
      @PropertyOption(name = VISIBILITY_PUBLIC, value = "The home is completely public.") })
  static final String VISIBILITY_PREFERENCE = "visibility.preference";    
  private String visibilityPreference;
  
  static final String PROFILE_IMPORT_TEMPLATE = "sakai.user.profile.template.default";
  static final String PROFILE_IMPORT_TEMPLATE_DEFAULT = "{'basic':{'elements':{'firstName':{'value':'@@firstName@@'},'lastName':{'value':'@@lastName@@'},'email':{'value':'@@email@@'}},'access':'everybody'}}";

  @Property(value = "/var/templates/pages/systemuser")
  public static final String DEFAULT_USER_PAGES_TEMPLATE = "default.user.template";
  private String defaultUserPagesTemplate;

  @Property(value = "/var/templates/pages/systemgroup")
  public static final String DEFAULT_GROUP_PAGES_TEMPLATE = "default.group.template";
  private String defaultGroupPagesTemplate;

  private String defaultProfileTemplate;
  private ArrayList<String> profileParams = new ArrayList<String>();

  private static final Logger LOGGER = LoggerFactory
      .getLogger(DefaultPostProcessor.class);

  /**
   * Principals that dont manage, Admin has permissions everywhere already. 
   */
  private static final Set<String> NO_MANAGE = ImmutableSet.of(Group.EVERYONE, User.ANON_USER, User.ADMIN_USER);

  private static final String JOINREQUESTS_FOLDER = "/joinrequests";

  private static final String JOINREQUESTS_RT = "sparse/joinrequests";

  @Reference
  protected Repository repository;

  @Reference
  protected EventAdmin eventAdmin;


  @Activate
  @Modified
  protected void modified(Map<?, ?> props) throws Exception {

    visibilityPreference = PropertiesUtil.toString(props.get(VISIBILITY_PREFERENCE),
        VISIBILITY_PREFERENCE_DEFAULT);

    defaultProfileTemplate = PROFILE_IMPORT_TEMPLATE_DEFAULT;

    int startPos = defaultProfileTemplate.indexOf("@@");
    while (startPos > -1) {
      int endPos = defaultProfileTemplate.indexOf("@@", startPos + 2);
      if (endPos > -1) {
        String param = defaultProfileTemplate.substring(startPos + 2, endPos);
        profileParams.add(param);

        endPos = defaultProfileTemplate.indexOf("@@", endPos + 2);
      }
      startPos = endPos;
    }

    defaultUserPagesTemplate = PropertiesUtil.toString(props.get(DEFAULT_USER_PAGES_TEMPLATE),
        "");
    defaultGroupPagesTemplate = PropertiesUtil.toString(
        props.get(DEFAULT_GROUP_PAGES_TEMPLATE), "");

    createDefaultUsers();

  }

  private void createDefaultUsers() throws Exception {
    Session session = repository.loginAdministrative();
    try {
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      Authorizable admin = authorizableManager.findAuthorizable(User.ADMIN_USER);
      Map<String, Object[]> adminMap = ImmutableMap.of("email", new Object[]{"admin@sakai.invalid"},
          "firstName", new Object[]{"Admin"},
          "lastName", new Object[]{"User"},
          "sakai:search-exclude-tree", new Object[]{true},
          ":sakai:profile-import", new Object[]{"{'basic': {'access': 'everybody', 'elements': {'email': {'value': 'admin@sakai.invalid'}, 'firstName': {'value': 'Admin'}, 'lastName': {'value': 'User'}}}}"});
      process(admin, session, Modification.onCreated("admin"), adminMap);
      Authorizable anon = authorizableManager.findAuthorizable(User.ANON_USER);
      Map<String, Object[]> anonMap = ImmutableMap.of("email", new Object[]{"anon@sakai.invalid"},
          "firstName", new Object[]{"Anon"},
          "lastName", new Object[]{"User"},
          "sakai:search-exclude-tree", new Object[]{true},
          ":sakai:profile-import", new Object[]{"{'basic': {'access': 'everybody', 'elements': {'email': {'value': 'anon@sakai.invalid'}, 'firstName': {'value': 'Anon'}, 'lastName': {'value': 'User'}}}}"});
      process(anon, session, Modification.onCreated("anon"), anonMap);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (Exception e) {
          LOGGER.debug(e.getMessage(),e);
        }
      }
    }
  }

  public void process(Authorizable authorizable,
      Session session, Modification change, Map<String, Object[]> parameters)
      throws Exception {
    LOGGER.debug("Default Post processor on {} with {} ", authorizable.getId(), change);

    if (parameters.containsKey(PARAM_AUTHORIZABLE_ONLY)) {
      String paramAuthorizableOnly = (String) parameters.get(PARAM_AUTHORIZABLE_ONLY)[0];
      if ("true".equalsIgnoreCase(paramAuthorizableOnly)) {
        LOGGER.info("Authorizable-only mode specified; skipping Home folder post-processing for {}",
            authorizable.getId());
        return;
      }
    }

    Session adminSession = null;
    try {
      ContentManager contentManager = session.getContentManager();
      AccessControlManager accessControlManager = session.getAccessControlManager();
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      adminSession = session.getRepository().loginAdministrative();
      boolean isGroup = authorizable instanceof Group;

      // WARNING: Creation and Update requests are more disjunct than is usual.
      //
      // In our current (bad) API, the only way to create a collaborative space (home
      // folder, messaging, discussions, web pages, contacts, roles) is as a side-effect of
      // creating an Authorizable. Because we want to let integrators create Group spaces
      // without necessarily becoming Group members, this is done in an administrative session.
      //
      // After the collaborative space is created, its various functional areas are managed
      // by specialized servlets and bundles. POSTs to an Authorizable are generally
      // only updates to that Authorizable object and should not require implicit access
      // to resources owned by the Authorizable. In particular, a request to add a member to
      // an existing Group Authorizable should not implicitly create a Home folder, pages,
      // contact lists, etc., for the Authorizable. (E.g., adding a member to the
      // internally-generated Group Authorizable that holds personal connections should not
      // recursively generate yet another connections-holding Group.)
      //
      // TODO We plan to replace this client-server API with a template-based approach that
      // decouples Authorizable management from collaborative space creation.
      boolean isCreate = ModificationType.CREATE.equals(change.getType());

      // If the sessionw as capable of performing the create or modify operation, it must be
      // capable of performing these operations.
      String authId = authorizable.getId();
      String homePath = LitePersonalUtils.getHomePath(authId);

      // User Authorizable PostProcessor
      // ==============================
      // no action required

      // Group Authorizable PostProcessor
      // ==============================
      // no action required (IMO we should drop the generated group and use ACL on the
      // object itself)
      if (isGroup) {
        if (isCreate) {
          setGroupManagers(authorizable, authorizableManager, accessControlManager,
              parameters);
        } else {
          updateManagersGroup(authorizable, authorizableManager, accessControlManager,
              parameters);
        }
      }

      // Home Authorizable PostProcessor
      // ==============================
      // home path
      if (!contentManager.exists(homePath)) {
        if (isCreate) {
          Builder<String, Object> props = ImmutableMap.builder();
          if (isGroup) {
            props.put(SLING_RESOURCE_TYPE, SAKAI_GROUP_HOME_RT);

          } else {
            props.put(SLING_RESOURCE_TYPE, SAKAI_USER_HOME_RT);
          }
          if (authorizable.hasProperty(SAKAI_SEARCH_EXCLUDE_TREE_PROP)) {
            // raw copy
            props.put(SAKAI_SEARCH_EXCLUDE_TREE_PROP,
                authorizable.getProperty(SAKAI_SEARCH_EXCLUDE_TREE_PROP));
          }
          contentManager.update(new Content(homePath, props.build()));

          List<AclModification> aclModifications = new ArrayList<AclModification>();
          // KERN-886 : Depending on the profile preference we set some ACL's on the profile.
          if (User.ANON_USER.equals(authId)) {
            AclModification.addAcl(true, Permissions.CAN_READ, User.ANON_USER,
                aclModifications);
            AclModification.addAcl(true, Permissions.CAN_READ, Group.EVERYONE,
                aclModifications);
          } else if (VISIBILITY_PUBLIC.equals(visibilityPreference)) {
            AclModification.addAcl(true, Permissions.CAN_READ, User.ANON_USER,
                aclModifications);
            AclModification.addAcl(true, Permissions.CAN_READ, Group.EVERYONE,
                aclModifications);
          } else if (VISIBILITY_LOGGED_IN.equals(visibilityPreference)) {
            AclModification.addAcl(false, Permissions.CAN_READ, User.ANON_USER,
                aclModifications);
            AclModification.addAcl(true, Permissions.CAN_READ, Group.EVERYONE,
                aclModifications);
          } else if (VISIBILITY_PRIVATE.equals(visibilityPreference)) {
            AclModification.addAcl(false, Permissions.CAN_READ, User.ANON_USER,
                aclModifications);
            AclModification.addAcl(false, Permissions.CAN_READ, Group.EVERYONE,
                aclModifications);
          }

          Map<String, Object> acl = Maps.newHashMap();
          syncOwnership(authorizable, acl, aclModifications);

          AclModification[] aclMods = aclModifications
              .toArray(new AclModification[aclModifications.size()]);
          accessControlManager.setAcl(Security.ZONE_CONTENT, homePath, aclMods);

          accessControlManager.setAcl(Security.ZONE_AUTHORIZABLES, authorizable.getId(),
              aclMods);

          // Create standard Home subpaths
          createPath(authId, LitePersonalUtils.getPublicPath(authId), SAKAI_PUBLIC_RT, false,
              contentManager, accessControlManager, null);
          createPath(authId, LitePersonalUtils.getPrivatePath(authId), SAKAI_PRIVATE_RT, true,
              contentManager, accessControlManager, null);

          // Message PostProcessor
          createPath(authId, homePath + MESSAGE_FOLDER, SAKAI_MESSAGESTORE_RT, true,
              contentManager, accessControlManager, null);
          // Calendar
          createPath(authId, homePath + CALENDAR_FOLDER, SAKAI_CALENDAR_RT, false,
              contentManager, accessControlManager, null);
          // Connections
          createPath(authId, homePath + CONTACTS_FOLDER, SAKAI_CONTACTSTORE_RT, true,
              contentManager, accessControlManager, null);
          authorizableManager.createGroup("g-contacts-" + authorizable.getId(), "g-contacts-"
              + authorizable.getId(), null);
          // Profile
          String profileType = (authorizable instanceof Group) ? SAKAI_GROUP_PROFILE_RT
                                                              : SAKAI_USER_PROFILE_RT;
          // FIXME BL120 this is a hackaround to KERN-1569; UI needs to change behavior
          final Map<String, Object> sakaiAuthzProperties = new HashMap<String, Object>();
          sakaiAuthzProperties.put("homePath", LitePersonalUtils.getHomeResourcePath(authId));
          if (authorizable instanceof Group) {
            for (final Entry<String, Object> entry : authorizable.getSafeProperties()
                .entrySet()) {
              final String key = entry.getKey();
              if (key.startsWith("sakai:group") || "sakai:pages-visible".equals(key)) {
                sakaiAuthzProperties.put(key, entry.getValue());
              }
            }
            createPath(authId, LitePersonalUtils.getPublicPath(authId) + PROFILE_FOLDER,
                profileType, false, contentManager, accessControlManager,
                sakaiAuthzProperties);
            createPath(authId, homePath + JOINREQUESTS_FOLDER, JOINREQUESTS_RT, false,
                contentManager, accessControlManager, null);
          } else {
            createPath(authId, LitePersonalUtils.getPublicPath(authId) + PROFILE_FOLDER,
                profileType, false, contentManager, accessControlManager, sakaiAuthzProperties);
          }
          // end KERN-1569 hackaround
          // createPath(authId, LitePersonalUtils.getPublicPath(authId) + PROFILE_FOLDER,
          // profileType, false, contentManager, accessControlManager, null);

          Map<String, Object> profileProperties = processProfileParameters(
            authorizable, parameters);
          createPath(authId, LitePersonalUtils.getProfilePath(authId) + PROFILE_BASIC,
              "nt:unstructured", false, contentManager, accessControlManager, null);
          for (Entry<String, Object> property : profileProperties.entrySet()) {
            String propName = property.getKey();
            createPath(authId, LitePersonalUtils.getProfilePath(authId) + PROFILE_BASIC + "/" + propName,
 "nt:unstructured", false, contentManager,
                accessControlManager, ImmutableMap.of("value", property.getValue()));
            authorizable.setProperty(propName, property.getValue());
          }
          authorizableManager.updateAuthorizable(authorizable);
          if ( isCreate ) {
            if ( isGroup ) {
              ActivityUtils.postActivity(eventAdmin, session.getUserId(), homePath, "Authorizable", "default", "group", "GROUP_CREATED", null);
            } else {
              ActivityUtils.postActivity(eventAdmin, session.getUserId(), homePath, "Authorizable", "default", "user", "USER_CREATED", null);
            }
          } else {
            if ( isGroup ) {
              ActivityUtils.postActivity(eventAdmin, session.getUserId(), homePath, "Authorizable", "default", "group", "GROUP_UPDATED", null);
            } else {
              ActivityUtils.postActivity(eventAdmin, session.getUserId(), homePath, "Authorizable", "default", "user", "USER_UPDATED", null);
            }
          }
        }
      } else {
        // Attempt to sync the Acl on the home folder with whatever is present in the
        // authorizable permissions. This is done for backwards compatibility. It
        // will not succeed if the current session has write access to the Authorizable
        // but lacks write access to the Home folder.
        //
        // TODO Consider dropping this feature since the Home path's ACL can be
        // explicitly modified in a Batch POST.

        Map<String, Object> acl = accessControlManager.getAcl(Security.ZONE_CONTENT,
            homePath);
        List<AclModification> aclModifications = new ArrayList<AclModification>();

        syncOwnership(authorizable, acl, aclModifications);

        try {
          accessControlManager.setAcl(Security.ZONE_CONTENT, homePath,
              aclModifications.toArray(new AclModification[aclModifications.size()]));
        } catch (AccessDeniedException e) {
          LOGGER.warn("User {} is not able to update ACLs for the Home path of Authorizable {} - exception {}",
              new Object[] {session.getUserId(), authorizable.getId(), e.getMessage()});
        }

        acl = accessControlManager
            .getAcl(Security.ZONE_AUTHORIZABLES, authorizable.getId());
        aclModifications = new ArrayList<AclModification>();

        syncOwnership(authorizable, acl, aclModifications);

        accessControlManager.setAcl(Security.ZONE_AUTHORIZABLES, authorizable.getId(),
            aclModifications.toArray(new AclModification[aclModifications.size()]));
      }
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }

  }

  /**
   * Create the managers group. Note, this is deprecated since this is not how
   * we will do this longer term.
   *
   * @param authorizable
   * @param authorizableManager
   * @param accessControlManager
   * @param parameters
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  @Deprecated
  private void setGroupManagers (Authorizable authorizable,
      AuthorizableManager authorizableManager, AccessControlManager accessControlManager,
      Map<String, Object[]> parameters) throws AccessDeniedException,
      StorageClientException {
    // if authorizable.getId() is unique, then it only has 1 manages group, which is
    // also unique by definition.
    Set<String> managers = Sets.newHashSet(StorageClientUtils.nonNullStringArray(
        (String[])authorizable.getProperty(UserConstants.PROP_GROUP_MANAGERS)));

    Object[] addValues = parameters.get(PARAM_ADD_TO_MANAGERS_GROUP);
    if ((addValues != null) && (addValues instanceof String[])) {
      for (String memberId : (String[]) addValues) {
        Authorizable toAdd = authorizableManager.findAuthorizable(memberId);
        managers.add(memberId);
        if (toAdd != null) {
          ((Group) authorizable).addMember(toAdd.getId());
        } else {
          LOGGER.warn("Could not add manager {} group {}", memberId,
              authorizable.getId());
        }
      }
    }
    authorizable.setProperty(UserConstants.PROP_GROUP_MANAGERS,
        managers.toArray(new String[managers.size()]));
    authorizableManager.updateAuthorizable(authorizable);

    // grant the mangers management over this group
    for (String managerId : managers) {
    accessControlManager.setAcl(
        Security.ZONE_AUTHORIZABLES,
        authorizable.getId(),
        new AclModification[] { new AclModification(AclModification
            .grantKey(managerId), Permissions.CAN_MANAGE.getPermission(),
            Operation.OP_REPLACE) });
    }

  }

  /**
   * If requested, update the managers group. Note, this is deprecated since this is not how
   * we will do this longer term.
   *
   * @param authorizable
   * @param authorizableManager
   * @param accessControlManager
   * @param parameters
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  @Deprecated
  private void updateManagersGroup (Authorizable authorizable,
      AuthorizableManager authorizableManager, AccessControlManager accessControlManager,
      Map<String, Object[]> parameters) throws AccessDeniedException,
      StorageClientException {
    boolean isUpdateNeeded = false;
    Object[] removeValues = parameters.get(PARAM_REMOVE_FROM_MANAGERS_GROUP);
    if ((removeValues != null) && (removeValues instanceof String[])) {
      isUpdateNeeded = true;
      for (String memberId : (String[]) removeValues) {
        ((Group) authorizable).removeMember(memberId);
      }
    }
    Object[] addValues = parameters.get(PARAM_ADD_TO_MANAGERS_GROUP);
    if ((addValues != null) && (addValues instanceof String[])) {
      isUpdateNeeded = true;
      for (String memberId : (String[]) addValues) {
        Authorizable toAdd = authorizableManager.findAuthorizable(memberId);
        if (toAdd != null) {
          ((Group) authorizable).addMember(toAdd.getId());
        } else {
          LOGGER.warn("Could not add manager {} to group {}", memberId,
              authorizable.getId());
        }
      }
    }
    if (isUpdateNeeded) {
      authorizableManager.updateAuthorizable(authorizable);
    }
  }

  private Map<String, Object> processProfileParameters(Authorizable authorizable, Map<String, Object[]> parameters) throws JSONException {
    Map<String, Object> retval = new HashMap<String, Object>();
    // default profile values
    // may be overwritten by the PROFILE_JSON_IMPORT_PARAMETER
    for (String param : profileParams) {
      Object val = "unknown";
      if (parameters.containsKey(param)) {
        val = parameters.get(param)[0];
      } else if (authorizable.hasProperty(param)) {
        val = authorizable.getProperty(param);
      }
      retval.put(param, val);
    }
    if (parameters.containsKey(PROFILE_JSON_IMPORT_PARAMETER)) {
      String profileJson = (String) parameters.get(PROFILE_JSON_IMPORT_PARAMETER)[0];
      JSONObject jsonObject = new JSONObject(profileJson);
      JSONObject basic = jsonObject.getJSONObject("basic");
      if (basic != null) {
        JSONObject elements = basic.getJSONObject("elements");
        if (elements != null) {
          for (Iterator<String> keys = elements.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object object = elements.get(key);
            if (object instanceof JSONObject) {
              JSONObject element = (JSONObject) object;
              retval.put(key, element.get("value"));
            } else {
              retval.put(key, object);
            }
          }
        }
      }
    }
    return retval;
  }

  private boolean createPath(String authId, String path, String resourceType,
      boolean isPrivate, ContentManager contentManager,
      AccessControlManager accessControlManager, Map<String, Object> additionalProperties)
      throws AccessDeniedException, StorageClientException {
    Builder<String, Object> propertyBuilder = ImmutableMap.builder();
    propertyBuilder.put(SLING_RESOURCE_TYPE, resourceType);
    if (additionalProperties != null) {
      propertyBuilder.putAll(additionalProperties);
    }
    if (!contentManager.exists(path)) {
      contentManager.update(new Content(path, propertyBuilder.build()));
      if (isPrivate) {
        accessControlManager.setAcl(
            Security.ZONE_CONTENT,
            path,
            new AclModification[] {
                new AclModification(AclModification.denyKey(User.ANON_USER),
                    Permissions.ALL.getPermission(), Operation.OP_REPLACE),
                new AclModification(AclModification.denyKey(Group.EVERYONE),
                    Permissions.ALL.getPermission(), Operation.OP_REPLACE),
                new AclModification(AclModification.grantKey(authId), Permissions.ALL
                    .getPermission(), Operation.OP_REPLACE) });
      }
      return true;
    }
    return false;
  }

  private void syncOwnership(Authorizable authorizable, Map<String, Object> acl,
      List<AclModification> aclModifications) throws StorageClientException, AccessDeniedException {
    boolean alreadySpecifiedAnonymousAcl = false;
    boolean alreadySpecifiedEveryoneAcl = false;
    for (AclModification aclMod : aclModifications) {
      if (aclMod.getAceKey().equals(AclModification.grantKey(User.ANON_USER))) {
        alreadySpecifiedAnonymousAcl = true;
      } else if (aclMod.getAceKey().equals(AclModification.denyKey(User.ANON_USER))) {
        alreadySpecifiedAnonymousAcl = true;
      } else if (aclMod.getAceKey().equals(AclModification.grantKey(Group.EVERYONE))) {
        alreadySpecifiedEveryoneAcl = true;
      } else if (aclMod.getAceKey().equals(AclModification.denyKey(Group.EVERYONE))) {
        alreadySpecifiedEveryoneAcl = true;
      }
    }
    // remove all acls we are not concerned with from the copy of the current state

    // make sure the owner has permission on their home
    if (authorizable instanceof User && !User.ANON_USER.equals(authorizable.getId())) {
      AclModification.addAcl(true, Permissions.ALL, authorizable.getId(),
          aclModifications);
    }

    Set<String> managerSettings = null;
    if (authorizable.hasProperty(UserConstants.PROP_GROUP_MANAGERS)) {
      managerSettings = ImmutableSet.copyOf((String[]) authorizable
          .getProperty(UserConstants.PROP_GROUP_MANAGERS));
    } else {
      managerSettings = ImmutableSet.of();
    }
    Set<String> viewerSettings;
    if (authorizable.hasProperty(UserConstants.PROP_GROUP_VIEWERS)) {
      viewerSettings = ImmutableSet.copyOf((String[]) authorizable
          .getProperty(UserConstants.PROP_GROUP_VIEWERS));
    } else {
      viewerSettings = ImmutableSet.of();
    }

    for (String key : acl.keySet()) {
      if (AclModification.isGrant(key)) {
        String principal = AclModification.getPrincipal(key);
        if (!NO_MANAGE.contains(principal) && !managerSettings.contains(principal)) {
          // grant permission is present, but not present in managerSettings, manage
          // ability (which include read ability must be removed)
          if (viewerSettings.contains(principal)) {
            aclModifications.add(new AclModification(key, Permissions.CAN_READ
                .getPermission(), Operation.OP_REPLACE));
          } else {
            aclModifications.add(new AclModification(key, Permissions.ALL.getPermission(), Operation.OP_DEL));
          }
        }
      }
    }
    for (String manager : managerSettings) {
      if (!acl.containsKey(AclModification.grantKey(manager))) {
        AclModification.addAcl(true, Permissions.CAN_MANAGE, manager, aclModifications);
      }
    }
    for (String viewer : viewerSettings) {
      if (!acl.containsKey(AclModification.grantKey(viewer))) {
        AclModification.addAcl(true, Permissions.CAN_READ, viewer, aclModifications);
      }
    }
    if (viewerSettings.size() > 0) {
      // ensure it's private unless specifically stated otherwise
      if (viewerSettings.contains(User.ANON_USER)) {
        // Make sure "anonymous" has read access.
        aclModifications.add(new AclModification(AclModification.denyKey(User.ANON_USER),
                                                 Permissions.ALL.getPermission(), Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.grantKey(User.ANON_USER),
                                                 Permissions.CAN_READ.getPermission(), Operation.OP_REPLACE));
      } else if (!alreadySpecifiedAnonymousAcl) {
        // Deny "anonymous" access to everything
        aclModifications.add(new AclModification(
                                                 AclModification.grantKey(User.ANON_USER), Permissions.ALL.getPermission(),
                                                 Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.denyKey(User.ANON_USER),
            Permissions.ALL.getPermission(), Operation.OP_REPLACE));
      }
      if (viewerSettings.contains(Group.EVERYONE)) {
        // Make sure "everyone" has read access.
        aclModifications.add(new AclModification(AclModification.denyKey(Group.EVERYONE),
                                                 Permissions.ALL.getPermission(), Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.grantKey(Group.EVERYONE),
                                                 Permissions.CAN_READ.getPermission(), Operation.OP_REPLACE));
      } else if (!alreadySpecifiedEveryoneAcl) {
        // Deny "everyone" access to everything
        aclModifications.add(new AclModification(
                                                 AclModification.grantKey(Group.EVERYONE), Permissions.ALL.getPermission(),
                                                 Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.denyKey(Group.EVERYONE),
            Permissions.ALL.getPermission(), Operation.OP_REPLACE));
      }
    } else {
      // assuming the permissions have not been set already, anon and everyone can read
      if (!alreadySpecifiedAnonymousAcl) {
        aclModifications.add(new AclModification(AclModification.denyKey(User.ANON_USER),
            Permissions.ALL.getPermission(), Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.grantKey(User.ANON_USER),
            Permissions.CAN_READ.getPermission(), Operation.OP_REPLACE));
      }
      if (!alreadySpecifiedEveryoneAcl) {
        aclModifications.add(new AclModification(AclModification.denyKey(Group.EVERYONE),
            Permissions.ALL.getPermission(), Operation.OP_DEL));
        aclModifications.add(new AclModification(AclModification.grantKey(Group.EVERYONE),
            Permissions.CAN_READ.getPermission(), Operation.OP_REPLACE));
      }

    }

    LOGGER.debug("Viewer Settings {}", viewerSettings);
    LOGGER.debug("Manager Settings {}", managerSettings);
    for (AclModification a : aclModifications) {
      LOGGER.debug("     Change {} ", a);
    }

  }
}

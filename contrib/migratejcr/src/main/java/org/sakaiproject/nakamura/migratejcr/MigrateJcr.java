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
package org.sakaiproject.nakamura.migratejcr;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap.Builder;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permission;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.Binary;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

/**
 *
 */
@Component
@Reference(name = "SlingRepository", referenceInterface = SlingRepository.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC, bind = "addRepo", unbind = "removeRepo")
public class MigrateJcr {
  private static final String SAKAI_POOLED_CONTENT_VIEWER = "sakai:pooled-content-viewer";
  private static final String SAKAI_POOLED_CONTENT_MANAGER = "sakai:pooled-content-manager";
  private static final String SLING_RESOURCE_TYPE = "sling:resourceType";
  public static final String PROP_MANAGERS_GROUP = "sakai:managers-group";
  public static final String PROP_MANAGED_GROUP = "sakai:managed-group";
  public static final String PARAM_ADD_TO_MANAGERS_GROUP = ":sakai:manager";
  public static final String PARAM_REMOVE_FROM_MANAGERS_GROUP = PARAM_ADD_TO_MANAGERS_GROUP
      + "@Delete";

  private Logger LOGGER = LoggerFactory.getLogger(MigrateJcr.class);

  @Reference(target="(name=presparse)")
  private SlingRepository slingRepository;

  private SlingRepository newSlingRepository;

  @Reference
  private Repository sparseRepository;
  
  /**
   * This will contain Sling repositories.
   */
  private Map<SlingRepository, SlingRepository> repositories =
      new ConcurrentHashMap<SlingRepository, SlingRepository>();

  private Set<String> ignoreProps = ImmutableSet.of("jcr:content", "jcr:data",
      "jcr:mixinTypes", "rep:policy", "jcr:created", "jcr:primaryType");

  @Activate
  protected void activate(ComponentContext componentContext) {
    @SuppressWarnings("rawtypes")
    Dictionary componentProps = componentContext.getProperties();
    if (shouldMigrate(componentProps)) {
      try {
        for (Entry<SlingRepository, SlingRepository> repo : repositories.entrySet()) {
          if (!repo.equals(slingRepository)) {
            newSlingRepository = repo.getValue();
            break;
          }
        }
        migrateAuthorizables();
        migrateContentPool();
        migrateTags();
        cleanup();
      } catch (Exception e) {
        LOGGER.error("Failed data migration from JCR to Sparse.", e);
      }
    }
  }

  private void migrateTags() throws Exception {
    javax.jcr.Session preSparseSession = null;
    javax.jcr.Session newJackrabbitSession = null;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      preSparseSession = slingRepository.loginAdministrative("default");
      newJackrabbitSession = newSlingRepository.loginAdministrative("default");
      LOGGER.info("Exporting /tags from jackrabbit.");
      preSparseSession.exportSystemView("/tags", output, false, false);
      LOGGER.info("Importing /tags to nakamura");
      newJackrabbitSession.importXML("/tags", new ByteArrayInputStream(output.toByteArray()), ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);
    } finally {
      if (preSparseSession != null) {
        preSparseSession.logout();
      }
      if (newJackrabbitSession != null) {
        newJackrabbitSession.logout();
      }
      output.close();
    }
    
  }

  private void cleanup() {
    // TODO Auto-generated method stub

  }

  @SuppressWarnings("deprecation")
  private void migrateContentPool() throws Exception {
    LOGGER.info("beginning users and groups migration.");
    String contentPoolQuery = "//element(*, sakai:pooled-content) order by @jcr:score descending";
    javax.jcr.Session jcrSession = null;
    Session sparseSession = null;
    javax.jcr.security.AccessControlManager accessManager = null;
    try {
      jcrSession = slingRepository.loginAdministrative("default");
      sparseSession = sparseRepository.loginAdministrative();
      accessManager = AccessControlUtil.getAccessControlManager(jcrSession);
      QueryManager qm = jcrSession.getWorkspace().getQueryManager();
      Query q = qm.createQuery(contentPoolQuery, Query.XPATH);
      QueryResult result = q.execute();
      NodeIterator resultNodes = result.getNodes();
      String nodeWord = resultNodes.getSize() == 1 ? "node" : "nodes";
      LOGGER.info("found {} pooled content {} in Jackrabbit.", resultNodes.getSize(),
          nodeWord);
      while (resultNodes.hasNext()) {
        Node contentNode = resultNodes.nextNode();
        LOGGER.info(contentNode.getPath());
        copyNodeToSparse(contentNode, contentNode.getName(), sparseSession, accessManager);
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
      if (sparseSession != null) {
        sparseSession.logout();
      }
    }

  }

  private void copyNodeToSparse(Node contentNode, String path, Session session, javax.jcr.security.AccessControlManager accessManager)
      throws Exception {
    ContentManager contentManager = session.getContentManager();
    if (contentManager.exists(path)) {
      LOGGER
          .warn("Ignoring migration of content at path which already exists in sparsemap: "
              + path);
      return;
    }
    PropertyIterator propIter = contentNode.getProperties();
    Builder<String, Object> propBuilder = ImmutableMap.builder();
    while (propIter.hasNext()) {
      Property prop = propIter.nextProperty();
      if (ignoreProps.contains(prop.getName())) {
        continue;
      }
      Object value;
      if (prop.isMultiple()) {
        Value[] values = prop.getValues();
        if (values.length > 0 && values[0].getType() == PropertyType.STRING) {
          String[] valueStrings = new String[values.length];
          for (int i = 0; i < values.length; i++) {
            valueStrings[i] = values[i].getString();
          }
          value = valueStrings;
        } else {
          // TODO handle multi-value properties of other types
          continue;
        }
      } else {
        switch (prop.getType()) {
        case PropertyType.BINARY:
          value = prop.getBinary();
          break;
        case PropertyType.BOOLEAN:
          value = prop.getBoolean();
          break;
        case PropertyType.DATE:
          value = prop.getDate();
          break;
        case PropertyType.DECIMAL:
          value = prop.getDecimal();
          break;
        case PropertyType.DOUBLE:
          value = prop.getDouble();
          break;
        case PropertyType.LONG:
          value = prop.getLong();
          break;
        case PropertyType.STRING:
          value = prop.getString();
          break;
        default:
          value = "";
          break;
        }
      }
      propBuilder.put(prop.getName(), value);
    }
    path = applyAdditionalProperties(propBuilder, contentNode, path);
    Content sparseContent = new Content(path, propBuilder.build());
    if (contentNode.hasNode("jcr:content")) {
      Node fileContentNode = contentNode.getNode("jcr:content");
      Binary binaryData = fileContentNode.getProperty("jcr:data").getBinary();
      try {
        InputStream binaryStream = binaryData.getStream();
        contentManager.update(sparseContent);
        contentManager.writeBody(sparseContent.getPath(), binaryStream);
      } catch (Exception e) {
        LOGGER.error("Unable to write binary content for JCR path: "
            + fileContentNode.getPath());
      }
    } else {
      contentManager.update(sparseContent);
    }
    try {
      AccessControlManager sparseAccessControl = session.getAccessControlManager();
      List<AclModification> aclModifications = new ArrayList<AclModification>();
      LOGGER.debug("Reading access control policies for " + contentNode.getPath());
//      AccessControlPolicy[] accessPolicies = accessManager.getEffectivePolicies(contentNode
//          .getPath());
//      for (AccessControlPolicy policy : accessPolicies) {
//        if (policy instanceof AccessControlList) {
//          for (AccessControlEntry ace : ((AccessControlList) policy)
//              .getAccessControlEntries()) {
//            String principal = ace.getPrincipal().getName();
//            String permission = AccessControlUtil.isAllow(ace) ? AclModification
//                .grantKey(principal) : AclModification.denyKey(principal);
//            for (Privilege priv : ace.getPrivileges()) {
//              Permission sparsePermission = Permissions.parse(priv.getName());
//              if (sparsePermission != null) {
//                aclModifications.add(new AclModification(permission, sparsePermission
//                    .getPermission(), Operation.OP_AND));
//                LOGGER.debug("translating jcr permission to sparse: " + permission + " -- "
//                    + priv.getName());
//              }
//            }
//          }
//        }
//      }
      sparseAccessControl.setAcl(Security.ZONE_CONTENT, path,
          aclModifications.toArray(new AclModification[aclModifications.size()]));
    } catch (Exception e) {
      LOGGER.error("Failed to set sparse access control on " + path, e);
    }
    // make recursive call for child nodes
    // depth-first traversal
    NodeIterator nodeIter = contentNode.getNodes();
    while (nodeIter.hasNext()) {
      Node childNode = nodeIter.nextNode();
      if (ignoreProps.contains(childNode.getName())) {
        continue;
      }
      copyNodeToSparse(childNode, path + "/" + childNode.getName(), session, accessManager);
    }

  }

  private String applyAdditionalProperties(Builder<String, Object> propBuilder,
      Node contentNode, String path) throws Exception {
    String contentPath = path;
    if (contentNode.hasProperty(SLING_RESOURCE_TYPE) 
        && "sakai/contact".equals(contentNode.getProperty(SLING_RESOURCE_TYPE).getString())) {
      // sparse searches for contacts rely on the sakai:contactstorepath property
      String contactStorePath = "a:" + contentNode.getPath().substring(12, contentNode.getPath().indexOf("/", 12)) + "/contacts";
      propBuilder.put("sakai:contactstorepath", contactStorePath);
    } else if (contentNode.hasProperty(SLING_RESOURCE_TYPE) 
        && "sakai/message".equals(contentNode.getProperty(SLING_RESOURCE_TYPE).getString())) {
      String messageStorePath = "a:" + contentNode.getPath().substring(12, contentNode.getPath().indexOf("/", 12)) + "/message/";
      propBuilder.put("sakai:messagestore", messageStorePath);
      // we want to flatten the message boxes. No more sharding required.
      contentPath = messageStorePath + contentNode.getProperty("sakai:messagebox").getString() + "/" + contentNode.getName();
    } else if (contentNode.hasProperty(SLING_RESOURCE_TYPE) 
        && "sakai/resource-update".equals(contentNode.getProperty(SLING_RESOURCE_TYPE).getString())) {
      // the resource-update indexer _really_ wants there to be a timestamp property
      Calendar timestamp = new GregorianCalendar();
      if (contentNode.hasNode("jcr:content") 
          && contentNode.getNode("jcr:content").hasProperty("jcr:lastModified")) {
        timestamp = contentNode.getNode("jcr:content").getProperty("jcr:lastModified").getDate();
      }
      propBuilder.put("timestamp", timestamp);
    } else if (contentNode.hasProperty(SLING_RESOURCE_TYPE) 
        && "sakai/pooled-content".equals(contentNode.getProperty(SLING_RESOURCE_TYPE).getString())) {
      // since we introduced sparsemap, we also flattened the 
      // tree of content members into two properties on the content
      List<String> contentViewers = new ArrayList<String>();
      List<String> contentManagers = new ArrayList<String>();
      Node membersNode = contentNode.getNode("members");
      NodeIterator nodeIter = membersNode.getNodes();
      // traverse the sharded paths used in jackrabbit
      while (nodeIter.hasNext()) {
        NodeIterator firstShardIter = nodeIter.nextNode().getNodes();
        while (firstShardIter.hasNext()) {
          NodeIterator secondShardIter = firstShardIter.nextNode().getNodes();
          while (secondShardIter.hasNext()) {
            Node contentMemberNode = secondShardIter.nextNode();
            if (contentMemberNode.hasProperty(SAKAI_POOLED_CONTENT_MANAGER)) {
              Value[] managerValues = contentMemberNode.getProperty(SAKAI_POOLED_CONTENT_MANAGER).getValues();
              contentManagers.add(managerValues[0].getString());
            }
            if (contentMemberNode.hasProperty(SAKAI_POOLED_CONTENT_VIEWER)) {
              Value[] viewerValues = contentMemberNode.getProperty(SAKAI_POOLED_CONTENT_VIEWER).getValues();
              contentViewers.add(viewerValues[0].getString());
            }
          }
        }
      }
      propBuilder.put(SAKAI_POOLED_CONTENT_VIEWER, contentViewers.toArray(new String[contentViewers.size()]));
      propBuilder.put(SAKAI_POOLED_CONTENT_MANAGER, contentManagers.toArray(new String[contentManagers.size()]));
    }
    return contentPath;
  }

  @SuppressWarnings("deprecation")
  private void migrateAuthorizables() throws Exception {
    LOGGER.info("beginning users and groups migration.");
    javax.jcr.Session jcrSession = null;
    try {
      jcrSession = slingRepository.loginAdministrative("default");
      String usersQuery = "//*[@sling:resourceType='sakai/user-home'] order by @jcr:score descending";
      QueryManager qm = jcrSession.getWorkspace().getQueryManager();
      Query q = qm.createQuery(usersQuery, Query.XPATH);
      QueryResult result = q.execute();
      NodeIterator resultNodes = result.getNodes();
      String folderWord = resultNodes.getSize() == 1 ? "folder" : "folders";
      LOGGER.info("found {} user home {} in Jackrabbit.", resultNodes.getSize(),
          folderWord);
      while (resultNodes.hasNext()) {
        Node authHomeNode = resultNodes.nextNode();
        LOGGER.debug(authHomeNode.getPath());
        moveAuthorizableToSparse(authHomeNode,
            AccessControlUtil.getUserManager(jcrSession));
      }

      String groupsQuery = "//*[@sling:resourceType='sakai/group-home'] order by @jcr:score descending";
      q = qm.createQuery(groupsQuery, Query.XPATH);
      result = q.execute();
      resultNodes = result.getNodes();
      folderWord = resultNodes.getSize() == 1 ? "folder" : "folders";
      LOGGER.info("found {} group home {} in Jackrabbit.", resultNodes.getSize(),
          folderWord);
      while (resultNodes.hasNext()) {
        Node groupHomeNode = resultNodes.nextNode();
        LOGGER.debug(groupHomeNode.getPath());
        moveAuthorizableToSparse(groupHomeNode,
            AccessControlUtil.getUserManager(jcrSession));
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }

  }

  private void moveAuthorizableToSparse(Node authHomeNode, UserManager userManager)
      throws Exception {
    Session sparseSession = null;
    javax.jcr.security.AccessControlManager accessManager = null;
    try {
      sparseSession = sparseRepository.loginAdministrative();
      AuthorizableManager authManager = sparseSession.getAuthorizableManager();
      accessManager = AccessControlUtil
      .getAccessControlManager(authHomeNode.getSession());
      AccessControlManager sparseAccessManager = sparseSession.getAccessControlManager();
      boolean isUser = "sakai/user-home".equals(authHomeNode.getProperty(
          SLING_RESOURCE_TYPE).getString());
      Node profileNode = authHomeNode.getNode("public/authprofile");
      if (isUser) {
        String userId;
        String firstName;
        String lastName;
        String email;
        List<String> tagList;
        try {
          userId = profileNode.getProperty("rep:userId").getString();
          Node propNode = profileNode.getNode("basic/elements/firstName");
          firstName = propNode.getProperty("value").getString();
          propNode = profileNode.getNode("basic/elements/lastName");
          lastName = propNode.getProperty("value").getString();
          propNode = profileNode.getNode("basic/elements/email");
          email = propNode.getProperty("value").getString();
          tagList = new ArrayList<String>();
          if (profileNode.hasProperty("sakai:tag-uuid")) {
            Value[] tagUuids = profileNode.getProperty("sakai:tag-uuid").getValues();
            for (Value tagUuid : tagUuids) {
              tagList.add(tagUuid.getString());
            }
          }
        } catch (Exception e) {
          LOGGER.error("Failed getting basic profile information for profile {}. Won't create this user.", authHomeNode.getPath());
          return;
        }
        // TODO do we care about the password?
        if (authManager.createUser(userId, userId, "testuser", ImmutableMap.of(
            "firstName", (Object) firstName, "lastName", lastName, "email", email, "sakai:tag-uuid", tagList.toArray(new String[tagList.size()])))) {
          LOGGER.info("Created user {} {} {} {}", new String[]{userId, firstName, lastName, email});
          LOGGER.debug("Adding user home folder for " + userId);
          copyNodeToSparse(authHomeNode, "a:" + userId, sparseSession, accessManager);
        } else {
          LOGGER.info("User {} exists in sparse. Skipping it.", userId);
        }
      } else {
        // handling a group
        org.apache.jackrabbit.api.security.user.Authorizable group = null;
        String groupId;
        String groupTitle;
        groupId = profileNode.getProperty("sakai:group-id").getString();
        groupTitle = profileNode.getProperty("sakai:group-title").getString();
        group = userManager.getAuthorizable(groupId);
        Builder<String,Object> propBuilder = getPropsFromGroup(group, userManager);
        if (authManager.createGroup(groupId, groupTitle, propBuilder.build())) {
          LOGGER.info("Created group {} {}", groupId, groupTitle);
          Authorizable sparseGroup = authManager.findAuthorizable(groupId);
          portManagersGroup(sparseGroup, authManager, sparseAccessManager, userManager);
          if (group instanceof Group) {
            // add all memberships
            copyGroupMembers(authManager, group, sparseGroup);
          }
          LOGGER.debug("Adding group home folder for group {}", groupId);
          copyNodeToSparse(authHomeNode, "a:" + groupId, sparseSession, accessManager);
        } else {
          LOGGER.info("Group {} exists in sparse. Skipping it.", groupId);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failure moving authorizable to sparsemap: {}",
          authHomeNode.getPath(), e);
    } finally {
      if (sparseSession != null) {
        sparseSession.logout();
      }
    }

  }

  private void copyGroupMembers(AuthorizableManager authManager,
      org.apache.jackrabbit.api.security.user.Authorizable jcrGroup,
      Authorizable sparseGroup) throws RepositoryException, AccessDeniedException,
      StorageClientException {
    LOGGER.info("Adding members for group {}", sparseGroup.getId());
    Iterator<org.apache.jackrabbit.api.security.user.Authorizable> members = ((Group)jcrGroup).getMembers();
    while (members.hasNext()) {
      org.apache.jackrabbit.api.security.user.Authorizable member = members.next();
      Authorizable sparseMember = authManager.findAuthorizable(member.getID());
      if (sparseMember != null) {
        ((org.sakaiproject.nakamura.api.lite.authorizable.Group)sparseGroup).addMember(sparseMember.getId());
      } else {
        LOGGER.warn("Wanted to add user {} to group {} but couldn't find user in sparse.", member.getID(), sparseGroup.getId());
      }
    }
    authManager.updateAuthorizable(sparseGroup);
  }

  private Builder<String, Object> getPropsFromGroup(
      org.apache.jackrabbit.api.security.user.Authorizable group, UserManager userManager) throws ValueFormatException, IllegalStateException, RepositoryException {
    Builder<String, Object> propBuilder = ImmutableMap.builder();
    for (Iterator<String> it = group.getPropertyNames();it.hasNext();) {
      String propName = it.next();
      Value[] propertyValues = group.getProperty(propName);
      if (propertyValues.length == 1) {
        propBuilder.put(propName, propertyValues[0].getString());
      } else if (propertyValues.length > 1) {
        String[] propValue = new String[propertyValues.length];
        for (int i = 0; i < propertyValues.length; i++) {
          propValue[i] = propertyValues[i].getString();
        }
        propBuilder.put(propName, propValue);
      }
    }
    return propBuilder;
  }

  @SuppressWarnings("unchecked")
  private boolean shouldMigrate(Dictionary componentProps) {
    // TODO determine whether there is any migrating to do
    return true;
  }
  
  /**
   * @param transport
   */
  protected void removeRepo(SlingRepository repository) {
    repositories.remove(repository);
  }

  /**
   * @param transport
   */
  protected void addRepo(SlingRepository repository) {
    repositories.put(repository,repository);
  }
  
  private void portManagersGroup (Authorizable managedGroup,
      AuthorizableManager sparseAuthorizableManager, AccessControlManager sparseAccessControlManager, UserManager jcrUserManager) throws AccessDeniedException,
      StorageClientException, ValueFormatException, IllegalStateException, RepositoryException {
    if (managedGroup.hasProperty(PROP_MANAGERS_GROUP)) {
      boolean isUpdateNeeded = false;
      String managersGroupId = (String) managedGroup.getProperty(PROP_MANAGERS_GROUP);
      Group jcrManagersGroup = (Group) jcrUserManager.getAuthorizable(managersGroupId);
      
      // create the sparse equivalent of the managers group
      Builder<String, Object> propBuilder = getPropsFromGroup(jcrManagersGroup, jcrUserManager);
      sparseAuthorizableManager.createGroup(managersGroupId, managersGroupId, propBuilder.build());
      Authorizable sparseManagersGroup = sparseAuthorizableManager.findAuthorizable(managersGroupId);
      
      // copy the managers group members
      copyGroupMembers(sparseAuthorizableManager, jcrManagersGroup, sparseManagersGroup);
      
      // grant the mangers group management over this group
      sparseAccessControlManager.setAcl(
          Security.ZONE_AUTHORIZABLES,
          managedGroup.getId(),
          new AclModification[] { new AclModification(AclModification
              .grantKey(managersGroupId), Permissions.CAN_MANAGE.getPermission(),
              Operation.OP_REPLACE) });
      // and over itself
      sparseAccessControlManager.setAcl(
          Security.ZONE_AUTHORIZABLES,
          managersGroupId,
          new AclModification[] { new AclModification(AclModification
              .grantKey(managersGroupId), Permissions.CAN_MANAGE.getPermission(),
              Operation.OP_REPLACE) });
      if (isUpdateNeeded) {
        sparseAuthorizableManager.updateAuthorizable(sparseManagersGroup);
      }
    }
  }

}

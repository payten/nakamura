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
package org.sakaiproject.nakamura.migratetov1;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Bundle;

import java.lang.reflect.Field;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.ServletException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.GregorianCalendar;
import java.util.HashSet;
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

import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.sakaiproject.nakamura.api.lite.Configuration;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;

import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.lite.OSGiStoreListener;
import org.sakaiproject.nakamura.lite.storage.StorageClient;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClient;
import org.sakaiproject.nakamura.lite.types.Types;

/**
 *
 */

@SlingServlet(methods = "GET", paths = "/system/migratetov1", generateComponent=true)
public class Migrate extends SlingSafeMethodsServlet {
  private Logger LOGGER = LoggerFactory.getLogger(Migrate.class);

  @Reference
  private Repository targetRepository;

  @Reference
  StorageClientPool targetConnectionPool;

  @Reference
  ClusterTrackingService clusterTrackingService;

  String sourceSlingDir = "sling-migrateme";
  String sourceStoreDir = "store-migrateme";

  private RepositoryImpl sourceRepository;

  private Configuration configuration;
  private JDBCStorageClientPool sourceConnectionPool;

  // The source repository
  Session sourceSession;
  AuthorizableManager sourceAM;
  ContentManager sourceCM;
  AccessControlManager sourceACL;

  // The destination repository
  Session targetSession;
  AuthorizableManager targetAM;
  ContentManager targetCM;
  AccessControlManager targetACL;


  private void connectToSourceRepository()
    throws Exception
  {
    if (!new File(sourceSlingDir).exists()) {
      throw new RuntimeException ("Couldn't open source sling directory: " + sourceSlingDir);
    }

    if (!new File(sourceStoreDir).exists()) {
      throw new RuntimeException ("Couldn't open source store directory: " + sourceStoreDir);
    }



    sourceRepository = new RepositoryImpl();

    Field configField = RepositoryImpl.class.getDeclaredField("configuration");
    configField.setAccessible(true);
    configuration = (Configuration)configField.get(targetRepository);

    sourceConnectionPool = new JDBCStorageClientPool();
    ImmutableMap.Builder<String, Object> connectionProps = ImmutableMap.builder();
    connectionProps.put("jdbc-url", "jdbc:derby:" + sourceSlingDir + "/sparsemap/db");
    connectionProps.put("jdbc-driver", "org.apache.derby.jdbc.EmbeddedDriver");
    connectionProps.put("store-base-dir", sourceStoreDir);
    connectionProps.put("username", "sa");
    connectionProps.put("password", "");
    connectionProps.put("max-wait", 10);
    connectionProps.put("max-idle", 5);
    connectionProps.put("test-on-borrow", true);
    connectionProps.put("test-on-return", true);
    connectionProps.put("time-between-eviction-run", 60000);
    connectionProps.put("num-tests-per-eviction-run", 1000);
    connectionProps.put("min-evictable-idle-time-millis", 10000);
    connectionProps.put("test-while-idle", false);
    connectionProps.put("when-exhausted-action", "grow");
    connectionProps.put("long-string-size", 0);
    connectionProps.put("org.sakaiproject.nakamura.api.lite.Configuration", configuration);
    sourceConnectionPool.activate(connectionProps.build());

    sourceRepository.setConfiguration(configuration);
    sourceRepository.setConnectionPool(sourceConnectionPool);
    sourceRepository.setStorageListener(new OSGiStoreListener());


    sourceSession = sourceRepository.loginAdministrative();
    sourceAM = sourceSession.getAuthorizableManager();
    sourceCM = sourceSession.getContentManager();
    sourceACL = sourceSession.getAccessControlManager();

    targetSession = targetRepository.loginAdministrative();
    targetAM = targetSession.getAuthorizableManager();
    targetCM = targetSession.getContentManager();
    targetACL = targetSession.getAccessControlManager();
  }


  private boolean isBoringUser(String userId) {
    return (userId.equals(User.ADMIN_USER) ||
            userId.equals(User.ANON_USER) ||
            userId.equals(User.SYSTEM_USER));
  }


  private void importJson(String path, String jsonString) throws Exception
  {
    JSONObject json = new JSONObject(jsonString);
    new LiteJsonImporter().importContent(targetCM, json, path, true, true, true, targetACL);
  }


  private Content cloneContent(Content content) throws Exception
  {
    return new Content(content.getPath(),
                       content.getProperties());
  }


  private void migrateContentACL(Content content) throws Exception
  {
    migrateACL("n", "CO", content.getPath(), true);
  }

  private void migrateContent(Content content) throws Exception
  {
    if (content == null) {
      return;
    }

    LOGGER.info("Migrating content object: {}", content);

    // Migrate any versions of this object prior to the latest version
    int versionCount = 0;
    for (String versionId : sourceCM.getVersionHistory(content.getPath())) {
      LOGGER.info("Migrating version {} of content object {}", versionId, content.getPath());

      Content version = sourceCM.getVersion(content.getPath(), versionId);

      Map<String,Object> props = new HashMap(version.getOriginalProperties());

      if (versionCount == 0) {
        // We're going to replay the versions in reverse order, but that
        // generates a new bunch of version IDs.  Clear the version history UUID
        // to create a new list.

        props.remove("_versionHistoryId");
      }

      props.remove("_readOnly");
      version = new Content(version.getPath(), props);

      versionCount++;

      targetCM.update(cloneContent(version));

      InputStream versionInputStream = sourceCM.getVersionInputStream(content.getPath(), versionId);

      if (versionInputStream != null) {
        targetCM.writeBody(content.getPath(), versionInputStream);
        versionInputStream.close();
      }

      targetCM.saveVersion(content.getPath());
    }

    // Finally, having written the versions (if any) write the most recent state of the object.
    targetCM.update(cloneContent(content));
    if (sourceCM.hasBody(content.getPath(), null)) {
      InputStream is = sourceCM.getInputStream(content.getPath());
      targetCM.writeBody(content.getPath(), is);
      is.close();
    }

    migrateContentACL(content);
  }


  private List<Content> allChildren(Content content)
  {
    List<Content> result = new ArrayList<Content>(0);

    if (content != null) {
      result.add(content);

      Iterator<Content> it = content.listChildren().iterator();
      while (it.hasNext()) {
        result.addAll(allChildren(it.next()));
      }
    }

    return result;
  }


  private void syncMembers(Authorizable sourceGroup, Authorizable targetGroup) throws Exception
  {
    for (String userId : ((Group)sourceGroup).getMembers()) {
      ((Group)targetGroup).addMember(userId);
    }

    targetAM.updateAuthorizable(targetGroup);
  }


  private String generateWidgetId()
  {
    return ("id" + Math.round(Math.random() * 10000000));
  }


  private String readResource(String name) throws Exception
  {
    InputStream is = this.getClass().getClassLoader().getResourceAsStream(name);
    byte buf[] = new byte[4096];

    StringBuilder sb = new StringBuilder();
    while (true) {
      int count = is.read(buf);

      if (count < 0) {
        break;
      }

      sb.append(new String(buf, 0, count));
    }

    return sb.toString();
  }


  private String fillOutTemplate(String templatePath) throws Exception
  {
    String s = readResource(templatePath);

    for (int i = 0; i < 1000; i++) {
      s = s.replaceAll(("__ID_" + i + "__"),
                       generateWidgetId());
    }

    return s;
  }


  private void migrateUser(Authorizable user) throws Exception
  {
    String userId = user.getId();
    String userPath = "a:" + userId;
    String contactsGroup = "g-contacts-" + userId;

    Map<String,Object> props = new HashMap<String,Object>(user.getOriginalProperties());

    List<String> filtered = new ArrayList<String>();

    for (String membership : ((String)props.get("principals")).split(";")) {
      if (membership.endsWith("-managers")) {
        // Now "-manager"
        filtered.add(membership.substring(0, membership.length() - 1));
      } else {
        filtered.add(membership);
      }
    }

    props.put("principals", StringUtils.join(filtered, ";"));

    targetAM.createUser(userId, userId, "testuser", props);

    LOGGER.info("\nMigrating user: {}", user);

    migrateUserACL(user);

    // User home
    migrateContent(sourceCM.get(userPath));

    // Message box
    migrateContent(sourceCM.get(userPath + "/message"));

    // Authprofile nodes
    for (Content obj : allChildren(sourceCM.get(userPath))) {
      if (obj.getPath().matches(".*authprofile.*")) {
        migrateContent(obj);

        List<AclModification> acls = new ArrayList<AclModification>();
     
        if (obj.getPath().matches("(.*authprofile$|.*authprofile/basic.*)")) {
          // Set basic profile information readable to logged in users
          AclModification.addAcl(false,
                                 Permissions.CAN_READ,
                                 User.ANON_USER,
                                 acls);
          AclModification.addAcl(true,
                                 Permissions.CAN_READ,
                                 org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE,
                                 acls);
        } else {
          // And all others to contacts only
          AclModification.addAcl(false,
                                 Permissions.CAN_READ,
                                 User.ANON_USER,
                                 acls);
          AclModification.addAcl(false,
                                 Permissions.CAN_READ,
                                 org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE,
                                 acls);
          AclModification.addAcl(true, Permissions.CAN_READ, contactsGroup, acls);
        }

        AclModification.addAcl(true, Permissions.CAN_ANYTHING, userId, acls);
        targetACL.setAcl(Security.ZONE_CONTENT, obj.getPath(), acls.toArray(new AclModification[acls.size()]));
      }
    }

    // public profile
    for (Content obj : allChildren(sourceCM.get(userPath + "/public/profile"))) {
        migrateContent(obj);
    }

    // contact nodes
    for (Content obj : allChildren(sourceCM.get(userPath + "/contacts"))) {
      migrateContent(obj);
    }

    // contact groups
    targetAM.createGroup(contactsGroup, contactsGroup, null);
    Authorizable sourceGroup = sourceAM.findAuthorizable(contactsGroup);
    Authorizable targetGroup = targetAM.findAuthorizable(contactsGroup);
    if (sourceGroup != null && targetGroup != null) {
      syncMembers(sourceGroup, targetGroup);
    }

    // User pubspace
    importJson(userPath + "/public/pubspace", fillOutTemplate("user-pubspace-template.json"));

    // User privspace
    importJson(userPath + "/private/privspace", fillOutTemplate("user-privspace-template.json"));
  }


  private String getHashAlgorithm()
    throws Exception
  {
    StorageClient storageClient = targetConnectionPool.getClient();
    Field f = storageClient.getClass().getDeclaredField("rowidHash");
    f.setAccessible(true);

    return (String)f.get(storageClient);
  }


  private String rowHash(String keySpace, String columnFamily, String key)
    throws Exception
  {
    return StorageClientUtils.encode(MessageDigest.getInstance(getHashAlgorithm()).digest
                                     ((keySpace + ":" + columnFamily + ":" + key).getBytes("UTF-8")));
  }


  private void migrateRows(Connection source, Connection dest, String table, String rid, boolean force)
    throws Exception
  {
    PreparedStatement sourceRows = source.prepareStatement("select rid, b from " + table + " where rid = ?");
    PreparedStatement delete = dest.prepareStatement("delete from " + table + " where rid = ?");
    PreparedStatement insert = dest.prepareStatement("insert into " + table + " (rid, b) values (?, ?)");

    sourceRows.setString(1, rid);
    ResultSet rs = sourceRows.executeQuery();

    while (rs.next()) {
      delete.clearParameters(); delete.clearWarnings();
      insert.clearParameters(); insert.clearWarnings();

      LOGGER.info ("Migrating row {} with value {}", rs.getString(1), rs.getBytes(2));

      if (force) {
        delete.setString(1, rs.getString(1));
        delete.execute();
      }

      insert.setString(1, rs.getString(1));
      insert.setBytes(2, rs.getBytes(2));

      // FIXME: error checking would be nice
      insert.execute();
    }
  }

  // Hm.  Actually this throws dupe key errors and looks a bit like we don't
  // have to do it...
  private void migrateUserACL(Authorizable user) throws Exception
  {
    migrateACL("n", "AU", user.getId(), true);
  }

  
  private InputStream rewriteHash(InputStream in, String old_rid, String new_rid, String columnFamily)
    throws Exception
  {
    Map<String, Object> map = new HashMap<String, Object>();

    Types.loadFromStream(old_rid, map, in, columnFamily);
    return Types.storeMapToStream(new_rid, map, columnFamily);
  }


  private void migrateACL(String keySpace, String columnFamily, String id, boolean force)
    throws Exception
  {
    String old_rid;

    if (id != null && id.startsWith("/")) {
      old_rid = rowHash(keySpace, "ac", columnFamily + id);
    } else {
      old_rid = rowHash(keySpace, "ac", columnFamily + "/" + id);
    }

    String new_rid = rowHash(keySpace, "ac", columnFamily + ";" + id);

    Connection source = ((JDBCStorageClientPool)sourceConnectionPool).getConnection();
    Connection dest = ((JDBCStorageClientPool)targetConnectionPool).getConnection();

    PreparedStatement sourceRows = source.prepareStatement("select rid, b from AC_CSS_B where rid = ?");
    PreparedStatement delete = dest.prepareStatement("delete from AC_CSS_B where rid = ?");
    PreparedStatement insert = dest.prepareStatement("insert into AC_CSS_B (rid, b) values (?, ?)");

    sourceRows.setString(1, old_rid);
    ResultSet rs = sourceRows.executeQuery();

    while (rs.next()) {
      delete.clearParameters(); delete.clearWarnings();
      insert.clearParameters(); insert.clearWarnings();

      LOGGER.info ("Migrating row {} with value {}", rs.getString(1), rs.getBytes(2));

      if (force) {
        delete.setString(1, new_rid);
        delete.execute();
      }

      insert.setString(1, new_rid);
      insert.setBinaryStream(2, rewriteHash(rs.getBinaryStream(2), old_rid, new_rid, "ac"));

      // FIXME: error checking would be nice
      insert.execute();
    }
  }


  private void migrateAllUsers() throws Exception
  {
    int page = 0;

    while (true) {
      Iterator<Authorizable> it = sourceAM.findAuthorizable("_page", String.valueOf(page),
                                                            User.class);
      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Authorizable user = it.next();

        if (!isBoringUser(user.getId())) {
          migrateUser(user);
        }
      }

      if (processed == 0) {
        break;
      }

      page++;
    }
  }


  private void migratePooledContent() throws Exception
  {
    LOGGER.info("\n\nMigrating pooled content");

    int page = 0;

    while (true) {
      LOGGER.info("\n\n** Migrating content page: " + page);

      JDBCStorageClient storageClient = (JDBCStorageClient)sourceConnectionPool.getClient();
      Iterator<Map<String,Object>> it = storageClient.find("n", "cn",
                                                           ImmutableMap.of("sling:resourceType", (Object)"sakai/pooled-content",
                                                                           "_page", String.valueOf(page)));

      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Map<String,Object> contentMap = it.next();

        migrateContent(sourceCM.get((String)contentMap.get("_path")));
      }

      if (processed == 0) {
        break;
      }

      page++;
    }

    LOGGER.info("\n\nDONE: Migrating pooled content");
  }


  private void setWorldReadableGroupWritable(String poolId, Authorizable group)
    throws Exception
  {
    setWorldReadable(poolId);

    List<AclModification> acls = new ArrayList<AclModification>();

    AclModification.addAcl(true, Permissions.CAN_READ, group.getId() + "-member", acls);
    AclModification.addAcl(true, Permissions.ALL, group.getId() + "-manager", acls);

    targetACL.setAcl(Security.ZONE_CONTENT, poolId, acls.toArray(new AclModification[acls.size()]));
  }


  private void setWorldReadable(String poolId) throws Exception
  {
    List<AclModification> acls = new ArrayList<AclModification>();
     
    AclModification.addAcl(true, Permissions.CAN_READ, User.ANON_USER, acls);
    AclModification.addAcl(true, Permissions.CAN_READ, org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE, acls);

    targetACL.setAcl(Security.ZONE_CONTENT, poolId, acls.toArray(new AclModification[acls.size()]));
  }


  private void buildDocstructure(Authorizable group) throws Exception
  {
    String libraryPoolId = clusterTrackingService.getClusterUniqueId();
    String participantsPoolId = clusterTrackingService.getClusterUniqueId();

    importJson(libraryPoolId,
               readResource("group-library.json").replaceAll("__GROUP__", group.getId()));
    LOGGER.info("... done.\n");

    importJson(participantsPoolId,
               readResource("group-participants.json").replaceAll("__GROUP__", group.getId()));

    setWorldReadable(libraryPoolId);
    setWorldReadable(participantsPoolId);

    importJson("a:" + group.getId() + "/docstructure",
               (readResource("group-docstructure.json")
                .replaceAll("__LIBRARY_POOLID__", libraryPoolId)
                .replaceAll("__PARTICIPANTS_POOLID__", participantsPoolId)));
  }


  private String getMembersString(String groupName, String[] members)
  {
    List<String> filtered = new ArrayList<String>(members.length);

    for (String member : members) {
      if (!member.equals(groupName + "-managers")) {
        filtered.add(member);
      }
    }

    return StringUtils.join(filtered, ";");
  }


  private void addToDocstructure(Authorizable group, String pageId, String title, String poolId)
    throws Exception
  {
    String groupId = group.getId();
    String groupPath = "a:" + groupId;
    Content content = targetCM.get(groupPath + "/docstructure");
    JSONObject structure = new JSONObject((String)content.getProperty("structure0"));

    int maxOrder = -1;
    for (Iterator<String> k = structure.keys(); k.hasNext();) {
      int order = structure.getJSONObject(k.next()).getInt("_order");
      if (order > maxOrder) {
        maxOrder = order;
      }
    }

    JSONObject newNode = new JSONObject();
    newNode.put("_title", title);
    newNode.put("_order", maxOrder + 1);
    newNode.put("_nonEditable", false);
    newNode.put("_pid", poolId);
    newNode.put("_view", "[\"everyone\",\"anonymous\",\"-member\"]");
    newNode.put("_edit", "[\"-manager\"]");

    structure.put(pageId, newNode);

    content.setProperty("structure0", structure.toString());

    targetCM.update(content);
  }


  private String getPageContent(Content content) throws Exception
  {
    for (Content obj : content.listChildren()) {
      if ("sakai/pagecontent".equals(obj.getProperty("sling:resourceType"))) {
        return (String)obj.getProperty("sakai:pagecontent");
      }
    }

    LOGGER.error("Couldn't find page content for: {}", content);
    return "";
  }


  private void migrateContentTree(Content rootNode, String newRoot)
    throws Exception
  {
    int lastSlash = rootNode.getPath().lastIndexOf("/");
    String oldRoot = rootNode.getPath().substring(0, lastSlash + 1);
    String nodeName = rootNode.getPath().substring(lastSlash + 1);
    String newPath = newRoot + "/" + nodeName;

    targetCM.update(new Content(newPath, rootNode.getProperties()));

    for (Content child : rootNode.listChildren()) {
      migrateContentTree(child, newPath);
    }
  }


  private void migrateWidgetData(Authorizable group, String poolId)
    throws Exception
  {
    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    for (Content obj : allChildren(sourceCM.get(groupPath + "/pages/_widgets"))) {
      if (obj.getPath().matches("^.*/id[0-9]+$")) {
        // THINKE: this is going to link every embedded piece of content
        // for a group to every page in the group.  Does this matter?
        // Embedded content used to all get put into a shared pool under
        // the embedcontent widget, but now they're added to a node
        // under the page's pool id.  Not sure how to figure out which
        // embedded content belongs to which page without parsing the
        // page source...
        migrateContentTree(obj, poolId);
      }
    }
  }


  private void migratePage(Authorizable group, Content content) throws Exception
  {
    String path =  content.getPath();
    String pageId = path.substring(path.lastIndexOf("/") + 1);
    String creator = (String)content.getProperty("_createdBy");
    String contentId = generateWidgetId();
    String poolId = clusterTrackingService.getClusterUniqueId();

    LOGGER.info("\n\nWILL USE: {}", poolId);

    String structure = "{\"__PAGE_ID__\":{\"_title\":\"__PAGE_TITLE__\",\"_order\":0,\"_ref\":\"__CONTENT_ID__\",\"_nonEditable\":false,\"main\":{\"_title\":\"__PAGE_TITLE__\",\"_order\":0,\"_ref\":\"__CONTENT_ID__\",\"_nonEditable\":false}}}";
    structure = (structure
                 .replaceAll("__PAGE_ID__", pageId)
                 .replaceAll("__PAGE_TITLE__", (String)content.getProperty("pageTitle"))
                 .replaceAll("__CONTENT_ID__", contentId));

    // THINKE: does "permissions" need to be cleverer?
    targetCM.update(new Content(poolId,
                                new ImmutableMap.Builder<String,Object>()
                                .put("sakai:copyright", "creativecommons")
                                .put("sakai:custom-mimetype", "x-sakai/document")
                                .put("sakai:description", "")
                                .put("sakai:permissions"," public")
                                .put("sakai:pool-content-created-for", creator)
                                .put("sakai:pooled-content-file-name", pageId)
                                .put("sakai:pooled-content-manager",
                                     new String[] { group.getId() + "-manager", creator })
                                .put("sakai:pooled-content-viewer",
                                     new String[] { "anonymous", "everyone", group.getId() + "-member" })
                                .put("sling:resourceType", "sakai/pooled-content")
                                .put("structure0", structure)
                                .build()));

    setWorldReadableGroupWritable(poolId, group);

    targetCM.update(new Content(poolId + "/" + contentId,
                                new ImmutableMap.Builder<String,Object>()
                                .put("page", getPageContent(content))
                                .build()));

    addToDocstructure(group, pageId, (String)content.getProperty("pageTitle"), poolId);

    migrateWidgetData(group, poolId);
  }


  private void migratePages(Authorizable group) throws Exception
  {
    LOGGER.info ("Migrating pages for group: {}", group);

    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    for (Content obj : allChildren(sourceCM.get(groupPath))) {
      if ("sakai/page".equals(obj.getProperty("sling:resourceType")) &&
          !obj.getPath().matches("^.*/(about-this-group|group-dashboard)$")) {
        LOGGER.info ("\n\n\nMIGRATING PAGE: {}", obj);
        migratePage(group, obj);
        LOGGER.info ("\nDONE\n\n");
      }
    }
  }


  private void migrateGroup(Authorizable group) throws Exception
  {
    LOGGER.info ("Migrating group: {}", group);

    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    Map<String,Object> props = new HashMap<String,Object>(group.getOriginalProperties());

    props.remove("sakai:managers-group");

    props.put("members", getMembersString(groupId, ((String)props.get("members")).split(";")));

    props.put("sakai:roles", "[{\"id\":\"member\",\"roleTitle\":\"Members\",\"title\":\"Member\",\"allowManage\":false},{\"id\":\"manager\",\"roleTitle\":\"Managers\",\"title\":\"Manager\",\"allowManage\":true}]");

    List<String> managers = new ArrayList<String>();
    for (String elt : (String[])props.get("rep:group-managers")) {
      if ((groupId + "-managers").equals(elt)) {
        managers.add(groupId + "-manager");
      } else {
        managers.add(elt);
      }
    }

    props.put("rep:group-managers", managers.toArray(new String[managers.size()]));

    props.put("sakai:category", "group");
    props.put("sakai:templateid", "simplegroup");
    props.put("sakai:joinRole", "member");
    props.put("membershipsCount", 0);
    props.put("membersCount", 0);
    props.put("contentCount", 0);

    // Set the rep:group-viewers based on the group's visibility
    String visibility = (String)props.get("sakai:group-visible");

    if (visibility != null) {
      if (visibility.equals("logged-in-only")) {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     groupId});
      } else if (visibility.equals("members-only")) {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     groupId});
      } else {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     "anonymous",
                                                     groupId});
      }
    }

    targetAM.createGroup(groupId, (String)group.getProperty("sakai:group-title"), props);

    // Group members
    targetAM.createGroup(groupId + "-member",
                         String.format("%s (Members)", groupId),
                         new ImmutableMap.Builder<String,Object>()
                         .put("type", "g")
                         .put("principals", groupId)
                         .put("rep:group-viewers", props.get("rep:group-viewers"))
                         .put("rep:group-managers", props.get("rep:group-managers"))
                         .put("sakai:group-joinable", props.get("sakai:group-joinable"))
                         .put("sakai:group-id", groupId + "-member")
                         .put("sakai:group-title", String.format("%s (Members)", groupId))
                         .put("sakai:pseudogroupparent", groupId)
                         .put("contentCount", 0)
                         .put("sakai:pseudoGroup", true)
                         .put("name", groupId + "-member")
                         .put("email", "unknown")
                         .put("firstName", "unknown")
                         .put("lastName", "unknown")
                         .put("membershipsCount", 1)
                         .put("sakai:excludeSearch", true)
                         .put("sakai:group-visible", props.get("sakai:group-visible"))
                         .put("members", getMembersString(groupId, ((Group)group).getMembers()))
                         .build());


    // Group managers
    targetAM.createGroup(groupId + "-manager",
                         String.format("%s (Managers)", groupId),
                         new ImmutableMap.Builder<String,Object>()
                         .put("type", "g")
                         .put("principals", groupId)
                         .put("rep:group-viewers", props.get("rep:group-viewers"))
                         .put("rep:group-managers", props.get("rep:group-managers"))
                         .put("sakai:group-joinable", props.get("sakai:group-joinable"))
                         .put("sakai:group-id", groupId + "-manager")
                         .put("sakai:group-title", String.format("%s (Managers)", groupId))
                         .put("sakai:pseudogroupparent", groupId)
                         .put("contentCount", 0)
                         .put("sakai:pseudoGroup", true)
                         .put("name", groupId + "-manager")
                         .put("email", "unknown")
                         .put("firstName", "unknown")
                         .put("lastName", "unknown")
                         .put("membershipsCount", 1)
                         .put("sakai:excludeSearch", true)
                         .put("sakai:group-visible", props.get("sakai:group-visible"))
                         .put("members", getMembersString(groupId, ((Group)sourceAM.findAuthorizable(groupId + "-managers")).getMembers()))
                         .build());



    // Group home
    migrateContent(sourceCM.get(groupPath));


    // Authprofile nodes
    for (Content obj : allChildren(sourceCM.get(groupPath))) {
      if (obj.getPath().matches(".*authprofile.*")) {
        migrateContent(obj);
      }
    }

    // The group's profile picture (THINKME: is this a specific case of a more
    // general type of content that needs migrating too?)
    for (Content obj : allChildren(sourceCM.get(groupPath))) {
      if ((obj.getProperty("mimeType") != null && ((String)(obj.getProperty("mimeType"))).startsWith("image/")) ||
          (obj.getProperty("_mimeType") != null && ((String)obj.getProperty("_mimeType")).startsWith("image/"))) {
        migrateContent(obj);
      }
    }

    LOGGER.info("\n\nBuilding docstructure...");
    buildDocstructure(group);


    LOGGER.info("\n\nMigrating group pages...");
    migratePages(group);

    // tickle the indexer
    targetAM.updateAuthorizable(targetAM.findAuthorizable(groupId));
  }


  private void migrateAllGroups() throws Exception
  {
    LOGGER.info("\n\nMigrating Groups");

    int page = 0;

    while (true) {
      LOGGER.info("\n\n** Migrating group page: " + page);

      JDBCStorageClient storageClient = (JDBCStorageClient)sourceConnectionPool.getClient();
      Iterator<Map<String,Object>> it = storageClient.find("n", "cn",
                                                           ImmutableMap.of("sling:resourceType", (Object)"sakai/group-home",
                                                                           "_page", String.valueOf(page)));

      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Map<String,Object> groupMap = it.next();

        String groupName = ((String)groupMap.get("_path")).split(":", 2)[1];
        Authorizable group = sourceAM.findAuthorizable(groupName);

        targetAM.delete(groupName);
        targetAM.delete(groupName + "-member");
        targetAM.delete(groupName + "-manager");
        migrateGroup(group);
    }

      if (processed == 0) {
        break;
      }

      page++;
    }

    LOGGER.info("\n\nDONE: Migrating groups");
  }



  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException
  {
    try {
      LOGGER.info("Migrating!");

      connectToSourceRepository();
      migrateAllUsers();
      migratePooledContent();
      migrateAllGroups();

    } catch (Exception e) {
      LOGGER.error("Caught a top-level error: {}", e);
      e.printStackTrace();
    }
  }
}

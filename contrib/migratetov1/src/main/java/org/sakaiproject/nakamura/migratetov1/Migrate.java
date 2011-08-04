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
import org.sakaiproject.nakamura.lite.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.lite.OSGiStoreListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import org.sakaiproject.nakamura.lite.storage.StorageClient;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;


/**
 *
 */
@Component
public class Migrate {
  private Logger LOGGER = LoggerFactory.getLogger(Migrate.class);

  @Reference
  private Repository targetRepository;

  @Reference
  StorageClientPool targetConnectionPool;

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

      LOGGER.info("allChildren: {}", content);
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


  private String fillOutTemplate(String templatePath) throws Exception
  {
    InputStream is = this.getClass().getClassLoader().getResourceAsStream(templatePath);
    byte buf[] = new byte[4096];

    StringBuilder sb = new StringBuilder();
    while (true) {
      int count = is.read(buf);

      if (count < 0) {
        break;
      }

      sb.append(new String(buf, 0, count));
    }

    String s = sb.toString();

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

    targetAM.createUser(userId, userId, "testuser",
                        user.getOriginalProperties());

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
    String contactsGroup = "g-contacts-" + userId;
    targetAM.createGroup(contactsGroup, contactsGroup, null);
    Authorizable sourceGroup = sourceAM.findAuthorizable(contactsGroup);
    Authorizable targetGroup = targetAM.findAuthorizable(contactsGroup);
    if (sourceGroup != null && targetGroup != null) {
      syncMembers(sourceGroup, targetGroup);
    }

    // User pubspace
    JSONObject pubspace = new JSONObject(fillOutTemplate("user-pubspace-template.json"));
    new LiteJsonImporter().importContent(targetCM, pubspace, (userPath + "/public/pubspace"),
                                         true, true, true, targetACL);

    // User privspace
    JSONObject privspace = new JSONObject(fillOutTemplate("user-privspace-template.json"));
    new LiteJsonImporter().importContent(targetCM, privspace, (userPath + "/private/privspace"),
                                         true, true, true, targetACL);
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
    LOGGER.info("Hash algo is: " + getHashAlgorithm());

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

    LOGGER.info("*** SQL STARTING");

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

    LOGGER.info("*** SQL DONE");
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

    LOGGER.info("*** SQL STARTING - {} TO {}", old_rid, new_rid);

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

    LOGGER.info("*** SQL DONE");
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
    int page = 0;

    while (true) {
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
  }


  @Activate
  protected void activate(ComponentContext componentContext)
  {
    try {
      LOGGER.info("Migrating!");

      connectToSourceRepository();
      migrateAllUsers();
      migratePooledContent();

    } catch (Exception e) {
      LOGGER.error("Caught a top-level error: {}", e);
      e.printStackTrace();
    }
  }
}

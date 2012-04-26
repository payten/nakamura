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
package org.sakaiproject.nakamura.files.migrator;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;

import junit.framework.Assert;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class DocMigratorTest extends Assert {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigratorTest.class);

  private static final int ALL_ACCESS = 28679;
  
  private Repository repository;
  
  private DocMigrator docMigrator;

  private Session session;

  private JSONObject readJSONFromFile(String fileName) throws IOException, JSONException {
    InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
    return new JSONObject(IOUtils.readFully(in, "utf-8"));
  }

  @Before
  public void setup() throws Exception {
    docMigrator = new DocMigrator();
    repository = new BaseMemoryRepository().getRepository();
    docMigrator.repository = repository;
    session = repository.loginAdministrative();
  }

  @Test
  public void detect_requires_migration() throws Exception {
    boolean requiresMigration = docMigrator.fileContentNeedsMigration(new Content("/some/path",
        ImmutableMap.of("structure0", "{}", FilesConstants.SCHEMA_VERSION, (Object) 2)));
    assertFalse(requiresMigration);
  }
  
  @Test
  public void test_requires_migration() throws Exception {
    ContentManager contentManager = mock(ContentManager.class);
    boolean requiresMigration = docMigrator.requiresMigration(
        readJSONFromFile("StructureZero.json"), new Content("/foo/bar",
        ImmutableMap.of("_lastModifiedBy", (Object) "zach")), contentManager);
    assertTrue(requiresMigration);
  }

  @Test
  public void use_pure_java_migrator() throws Exception {
    JSONObject oldStructure = readJSONFromFile("SampleOldStructure.json");
    JSONObject structure0 = readJSONFromFile("StructureZero.json");
    JSONObject newStructure = docMigrator.createNewPageStructure(structure0, oldStructure);
    JSONObject convertedStructure = (JSONObject) docMigrator.convertArraysToObjects(newStructure);
    docMigrator.validateStructure(convertedStructure);
    assertEquals("googlemaps", newStructure.getJSONObject("id9642791")
      .getJSONObject("rows").getJSONObject("__array__0__")
      .getJSONObject("columns").getJSONObject("__array__0__")
      .getJSONObject("elements").getJSONObject("__array__1__")
      .getString("type"));
  }
  
  @Test
  public void test_doc_with_discussion() throws Exception {
    JSONObject docStructure = readJSONFromFile("DocWithAdditionalPage.json");
    JSONObject docStructureZero = readJSONFromFile("DocWithAdditionalPageStructureZero.json");
    JSONObject newStructure = docMigrator.createNewPageStructure(docStructureZero, docStructure);
    JSONObject convertedStructure = (JSONObject) docMigrator.convertArraysToObjects(newStructure);
    LOGGER.info(newStructure.toString(2));
    docMigrator.validateStructure(convertedStructure);
    assertTrue("We expect sakai:pooled-content-viewer to be an array of Strings.",
        convertedStructure.get("sakai:pooled-content-viewer") instanceof JSONArray);
  }
  
  @Test
  public void make_sure_croby_pubspace_will_migrate() throws Exception {
    final String CROBY_NAME = "croby";
    final String CROBY_PATH = "a:" + CROBY_NAME;
    final String CROBY_PUBSPACE_PATH = CROBY_PATH + "/public/pubspace";    
    session.getAuthorizableManager().createUser(CROBY_NAME, CROBY_NAME, "shhhh", null);
    session.getAccessControlManager().setAcl("CO", CROBY_PATH, new AclModification[]{new AclModification(CROBY_NAME + "@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    session.logout();
    session = repository.loginAdministrative(CROBY_NAME);
    ContentManager contentManager = session.getContentManager();
    AccessControlManager accessControlManager = session.getAccessControlManager();
    JSONObject crobyPubspace = readJSONFromFile("CrobyPubspace.json");
    LiteJsonImporter jsonImporter = new LiteJsonImporter();
    jsonImporter.importContent(contentManager, crobyPubspace, CROBY_PUBSPACE_PATH, true,
        true, false, true, accessControlManager, true);
    Content crobyPubspaceContent = contentManager.get(CROBY_PUBSPACE_PATH);
    assertTrue(docMigrator.fileContentNeedsMigration(crobyPubspaceContent));
    docMigrator.migrateFileContent(crobyPubspaceContent);
    crobyPubspaceContent = contentManager.get(CROBY_PUBSPACE_PATH);
    assertEquals(CROBY_NAME, crobyPubspaceContent.getProperty("_lastModifiedBy"));
  }

  @Test
  public void handle_missing_page_element() throws Exception {
    JSONObject docStructure = readJSONFromFile("MissingPageElement.json");
    JSONObject docStructureZero = readJSONFromFile("MissingPageElementStructureZero.json");
    JSONObject newStructure = docMigrator.createNewPageStructure(docStructureZero, docStructure);
  }
  
  @Test
  public void testListSpanningComments() throws Exception {
    // KERN-2672: list content that spans a comments widget goes missing after migration
    JSONObject doc = readJSONFromFile("ListSpanningComments.json"); 
    JSONObject migrated = docMigrator.createNewPageStructure(
        new JSONObject(doc.getString("structure0")), doc);
    LOGGER.info("Migrated kern2672=" + migrated.toString(2));
    assertEquals(2, migrated.getJSONObject("id4297137")
      .getJSONArray("rows").getJSONObject(0)
      .getJSONArray("columns").getJSONObject(0)
      .getJSONArray("elements").length());
  }

  @Test
  public void testCommentSettings() throws Exception {
    // KERN-2674: comments widget settings not honored after migration
    JSONObject doc = readJSONFromFile("CommentSettingsNotHonored.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
        new JSONObject(doc.getString("structure0")), doc);
    LOGGER.info("Migrated kern2674=" + migrated.toString(2));
    // TODO fix logic and write asserts to check it
  }

  @Test
  public void testDiscussionSettings() throws Exception {
    // KERN-2675: discussion widget settings not honored after migration
    JSONObject doc = readJSONFromFile("DiscussionSettingsNotHonored.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
        new JSONObject(doc.getString("structure0")), doc);
    LOGGER.info("Migrated kern2675=" + migrated.toString(2));
    // TODO fix logic and write asserts to check it
  }
  
  @Test
  public void test_content_from_json() throws Exception {
    Content testContent = docMigrator.contentFromJson(readJSONFromFile("CommentSettingsNotHonored.json"));
    assertEquals("kWlEwusoN", testContent.getPath());
    assertEquals("i72NwGeREeG8G6WPjdVxzA+", testContent.getId());
    assertTrue(testContent.getProperty("sakai:tags") instanceof String[]);
  }


  @Test
  public void isPageNode() throws Exception {
    final String DOC_PATH = "/p/12345test";
    repository = new BaseMemoryRepository().getRepository();
    docMigrator.repository = repository;
    Session session = repository.loginAdministrative();
    ContentManager contentManager = session.getContentManager();
    AccessControlManager accessControlManager = session.getAccessControlManager();
    JSONObject doc = readJSONFromFile("DocWithAdditionalPage.json");
    LiteJsonImporter jsonImporter = new LiteJsonImporter();
    jsonImporter.importContent(contentManager, doc, DOC_PATH, true, true, false, true,
        accessControlManager, true);
    Content docContent = contentManager.get(DOC_PATH);
    assertTrue(docMigrator.fileContentNeedsMigration(docContent));
    docMigrator.migrateFileContent(docContent);
    docContent = contentManager.get(DOC_PATH);

    Content subpage = contentManager.get(DOC_PATH + "/id2545619");
    assertTrue(docMigrator.isPageNode(subpage, contentManager));
    assertFalse(docMigrator.isPageNode(docContent, contentManager));

  }
  @Test
  public void testPageInStructureZeroIsMissing() throws Exception {
    // KERN-2687: structure0 contains a ref to a page (id1165301022) that's not present
    JSONObject doc = readJSONFromFile("PageInStructureZeroIsMissing.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
    new JSONObject(doc.getString("structure0")), doc);
    assertFalse(migrated.has("id1165301022"));
  }

  @Test
  public void testDiscussionWithNoMessages() throws Exception {
    // KERN-2678: migration blows up if we process a discussion widget without an "inbox" property
    JSONObject doc = readJSONFromFile("GroupMigrationTest.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
      new JSONObject(doc.getString("structure0")), doc);
  }
  
  @Test
  public void testGroupMigration() throws Exception {
    JSONObject group = readJSONFromFile("CoffeeNerdsGroup.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
      new JSONObject(group.getString("structure0")), group);
    assertEquals(1, migrated.getJSONObject("id27903150")
      .getJSONArray("rows").getJSONObject(0)
      .getJSONArray("columns").getJSONObject(0)
      .getJSONArray("elements").length());
  }

  @Test
  public void testBlockImageLeft() throws Exception {
    JSONObject group = readJSONFromFile("BlockLeft.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
      new JSONObject(group.getString("structure0")), group);
    assertEquals(1, migrated.getJSONObject("id9733210")
      .getJSONArray("rows").getJSONObject(0)
      .getJSONArray("columns").length());
    assertEquals(2, migrated.getJSONObject("id9733210")
      .getJSONArray("rows").getJSONObject(1)
      .getJSONArray("columns").length());
  }

  @Test
  public void testKern2759Redux() throws Exception {
    JSONObject group = readJSONFromFile("KERN2759Redux.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
      new JSONObject(group.getString("structure0")), group);
    assertEquals(1, migrated.getJSONObject("id6593845")
      .getJSONArray("rows").getJSONObject(0)
      .getJSONArray("columns").length());
    assertEquals(2, migrated.getJSONObject("id6593845")
      .getJSONArray("rows").getJSONObject(1)
      .getJSONArray("columns").length());
    assertEquals(0.5, migrated.getJSONObject("id6593845")
      .getJSONArray("rows").getJSONObject(1)
      .getJSONArray("columns").getJSONObject(0).getDouble("width"));
  }

  @Test
  public void testElementsInOrder() throws Exception {
    JSONObject group = readJSONFromFile("OutOfOrder.json");
    JSONObject migrated = docMigrator.createNewPageStructure(
      new JSONObject(group.getString("structure0")), group);
    assertEquals("remotecontent", migrated.getJSONObject("id9244742")
      .getJSONArray("rows").getJSONObject(0)
      .getJSONArray("columns").getJSONObject(0)
      .getJSONArray("elements").getJSONObject(2)
      .getString("type"));
  }
  
  @Test
  public void testBasicLtiSecretsMigrate() throws Exception {
    JSONObject page = readJSONFromFile("StructureWithBasicLti.json");
    LiteJsonImporter jsonImporter = new LiteJsonImporter();
    
    // import page setup content
    Session adminSession = repository.loginAdministrative();
    jsonImporter.importContent(adminSession.getContentManager(), page,
        "/testBasicLtiSecretsMigrate", true, true, true, adminSession.getAccessControlManager());
    
    // sanity check the import
    Assert.assertTrue(adminSession.getContentManager().exists("/testBasicLtiSecretsMigrate"));
    Assert.assertTrue(adminSession.getContentManager().exists("/testBasicLtiSecretsMigrate/id5404779"));
    Assert.assertTrue(adminSession.getContentManager().exists("/testBasicLtiSecretsMigrate/id5404779/basiclti"));
    Assert.assertTrue(adminSession.getContentManager().exists("/testBasicLtiSecretsMigrate/id5404779/basiclti/ltiKeys"));
    
    // create and grant access for "test" user
    adminSession.getAuthorizableManager().createUser("test", "test", "test",
        new HashMap<String, Object>());
    adminSession.getAccessControlManager().setAcl("CO", "/testBasicLtiSecretsMigrate",
        new AclModification[] { new AclModification("test@g", ALL_ACCESS,
            AclModification.Operation.OP_REPLACE) });
    
    // lock down the ltiKeys node, similar to how it would be in production
    accessControlSensitiveNode("/testBasicLtiSecretsMigrate/id5404779/basiclti/ltiKeys", adminSession,
        "test");
    adminSession.logout();
    
    // perform the migration as a non-admin user, that ltiKeys node still needs to migrate
    Session userSession = repository.login("test", "test");
    docMigrator.migrateFileContent(userSession.getContentManager().get(
        "/testBasicLtiSecretsMigrate"));
    userSession.logout();
    
    // ensure that the ltiKeys node was migrated
    adminSession = repository.loginAdministrative();
    Content ltiKeys = adminSession.getContentManager().get(
        "/testBasicLtiSecretsMigrate/id1587576/id5404779/basiclti/ltiKeys");
    Assert.assertNotNull(ltiKeys);
    Assert.assertEquals("the-key", ltiKeys.getProperty("key"));
    Assert.assertEquals("the-secret", ltiKeys.getProperty("secret"));
    adminSession.logout();
    
    // verify ltiKeys was locked down again to not expose lti secrets
    userSession = repository.login("test", "test");
    Assert.assertFalse(userSession.getContentManager().exists(
        "/testBasicLtiSecretsMigrate/id1587576/id5404779/basiclti/ltiKeys"));
  }
  
  /**
   * Apply the necessary access control entries so that only admin users can read/write
   * the sensitive node.
   * 
   * @param sensitiveNodePath
   * @param adminSession
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private void accessControlSensitiveNode(final String sensitiveNodePath,
      final Session adminSession, String currentUserId) throws StorageClientException,
      AccessDeniedException {

    adminSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        sensitiveNodePath,
        new AclModification[] {
            new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(currentUserId), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE) });
  }
}

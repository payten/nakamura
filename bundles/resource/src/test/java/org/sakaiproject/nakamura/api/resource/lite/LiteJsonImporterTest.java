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
package org.sakaiproject.nakamura.api.resource.lite;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.io.IOUtils;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;

public class LiteJsonImporterTest {

  private static final String[] TEST_FILES = {
    "testimport/test1.json",
    "testimport/test2.json",
    "testimport/test3.json",
    "testimport/test4.json",
    "testimport/test5.json"
    };
  private static final String TEST_IGNORE_BASE_FILE = "testimport/test-ignore-base.json";
  private static final String TEST_IGNORE_IMPORT_FILE = "testimport/test-ignore-import.json";
  private static final String TEST_IGNORE_IMPORT_ARRAY_FILE = "testimport/test-ignore-import-array.json";
  private static final String TEST_IGNORE_IMPORT_PRIM_FILE = "testimport/test-ignore-import-primitive.json";
  
  private RepositoryImpl repository;

  public LiteJsonImporterTest() throws AccessDeniedException, StorageClientException,
      ClassNotFoundException, IOException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    Session session = repository.loginAdministrative();
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    authorizableManager.createUser("ieb", "Ian Boston", "test",
        ImmutableMap.of("x", (Object) "y"));
    org.sakaiproject.nakamura.api.lite.authorizable.Authorizable authorizable = authorizableManager
        .findAuthorizable("ieb");
    System.err.println("Got ieb as " + authorizable);
    session.logout();

  }

  @Test
  public void testPath() {
    LiteJsonImporter liteImporter = new LiteJsonImporter();
    Assert.assertEquals(null, liteImporter.getPathElement(null));
    Assert.assertEquals(null, liteImporter.getPathElement(""));
    Assert.assertEquals(null, liteImporter.getPathElement("@"));
    Assert.assertEquals("", liteImporter.getPathElement("@x"));
    Assert.assertEquals("key", liteImporter.getPathElement("key"));
    Assert.assertEquals("key", liteImporter.getPathElement("key@Unknown"));

  }

  @Test
  public void testTypes() {
    LiteJsonImporter liteImporter = new LiteJsonImporter();
    Assert.assertEquals(Object.class, liteImporter.getElementType(null));
    Assert.assertEquals(Object.class, liteImporter.getElementType(""));
    Assert.assertEquals(Object.class, liteImporter.getElementType("@"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("@x"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("key"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("key@Unknown"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("key@grant"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("key@deny"));
    Assert.assertEquals(Object.class, liteImporter.getElementType("key@Delete"));
    Assert.assertEquals(String.class, liteImporter.getElementType("key@TypeString"));
    Assert.assertEquals(Integer.class, liteImporter.getElementType("key@TypeInteger"));
    Assert.assertEquals(Long.class, liteImporter.getElementType("key@TypeLong"));
    Assert.assertEquals(Double.class, liteImporter.getElementType("key@TypeDouble"));
    Assert.assertEquals(Boolean.class, liteImporter.getElementType("key@TypeBoolean"));
    Assert.assertEquals(BigDecimal.class,
        liteImporter.getElementType("key@TypeBigDecimal"));
    Assert.assertEquals(Calendar.class, liteImporter.getElementType("key@TypeDate"));
  }

  @Test
  public void testObject() {
    LiteJsonImporter liteImporter = new LiteJsonImporter();
    Assert.assertEquals("1", liteImporter.getObject("1", String.class));
    Assert.assertEquals((Long) 1L, liteImporter.getObject("1", Long.class));
    Assert.assertEquals((Integer) 1, liteImporter.getObject("1", Integer.class));
    Assert.assertEquals((Double) 1.0, liteImporter.getObject("1", Double.class));
    Assert.assertEquals(new BigDecimal("1.094"),
        liteImporter.getObject("1.094", BigDecimal.class));
    Assert.assertEquals((Boolean) true, liteImporter.getObject("true", Boolean.class));
    Calendar c = liteImporter.getObject("20110329T101523+0330", Calendar.class);
    Assert.assertEquals(2011, c.get(Calendar.YEAR));
    Assert.assertEquals(2, c.get(Calendar.MONTH));
    Assert.assertEquals(29, c.get(Calendar.DAY_OF_MONTH));
    Assert.assertEquals(10, c.get(Calendar.HOUR_OF_DAY));
    Assert.assertEquals(15, c.get(Calendar.MINUTE));
    Assert.assertEquals(23, c.get(Calendar.SECOND));
    Assert.assertEquals(((3 * 60 + 30) * 60) * 1000, c.get(Calendar.ZONE_OFFSET));
    c = liteImporter.getObject("20110329T101523Z", Calendar.class);
    Assert.assertEquals(2011, c.get(Calendar.YEAR));
    Assert.assertEquals(2, c.get(Calendar.MONTH));
    Assert.assertEquals(29, c.get(Calendar.DAY_OF_MONTH));
    Assert.assertEquals(10, c.get(Calendar.HOUR_OF_DAY));
    Assert.assertEquals(15, c.get(Calendar.MINUTE));
    Assert.assertEquals(23, c.get(Calendar.SECOND));
    Assert.assertEquals(0, c.get(Calendar.ZONE_OFFSET));
    c = liteImporter.getObject("20110329T101523-0330", Calendar.class);
    Assert.assertEquals(2011, c.get(Calendar.YEAR));
    Assert.assertEquals(2, c.get(Calendar.MONTH));
    Assert.assertEquals(29, c.get(Calendar.DAY_OF_MONTH));
    Assert.assertEquals(10, c.get(Calendar.HOUR_OF_DAY));
    Assert.assertEquals(15, c.get(Calendar.MINUTE));
    Assert.assertEquals(23, c.get(Calendar.SECOND));
    Assert.assertEquals(-((3 * 60 + 30) * 60) * 1000, c.get(Calendar.ZONE_OFFSET));
  }

  @Test
  public void testArray() throws JSONException {
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    JSONArray arr = new JSONArray(ImmutableList.of("1", "2", "3"));
    String[] s = liteJsonImporter.getArray(arr, String.class);
    Assert.assertArrayEquals(new String[] { "1", "2", "3" }, s);
    Integer[] ia = liteJsonImporter.getArray(arr, Integer.class);
    Assert.assertArrayEquals(new Integer[] { 1, 2, 3 }, ia);
    Long[] la = liteJsonImporter.getArray(arr, Long.class);
    Assert.assertArrayEquals(new Long[] { 1L, 2L, 3L }, la);
    arr = new JSONArray(ImmutableList.of("1.1", "2.2", "3.3"));
    Double[] da = liteJsonImporter.getArray(arr, Double.class);
    Assert.assertArrayEquals(new Double[] { 1.1, 2.2, 3.3 }, da);
    arr = new JSONArray(ImmutableList.of("true", "false", "0"));
    Boolean[] ba = liteJsonImporter.getArray(arr, Boolean.class);
    Assert.assertArrayEquals(new Boolean[] { true, false, false }, ba);
  }

  @Test
  public void testPermissionBitmap() throws JSONException {
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    Assert.assertEquals(Permissions.CAN_READ.getPermission(),
        liteJsonImporter.getPermissionBitMap(new JSONArray(ImmutableList.of("read"))));
    Assert.assertEquals(Permissions.CAN_WRITE.getPermission(),
        liteJsonImporter.getPermissionBitMap(new JSONArray(ImmutableList.of("write"))));
    Assert.assertEquals(Permissions.CAN_DELETE.getPermission(),
        liteJsonImporter.getPermissionBitMap(new JSONArray(ImmutableList.of("delete"))));
    Assert
        .assertEquals(Permissions.CAN_READ_ACL.getPermission(), liteJsonImporter
            .getPermissionBitMap(new JSONArray(ImmutableList.of("read-acl"))));
    Assert.assertEquals(Permissions.CAN_WRITE_ACL.getPermission(), liteJsonImporter
        .getPermissionBitMap(new JSONArray(ImmutableList.of("write-acl"))));
    Assert.assertEquals(Permissions.CAN_DELETE_ACL.getPermission(), liteJsonImporter
        .getPermissionBitMap(new JSONArray(ImmutableList.of("delete-acl"))));
    Assert.assertEquals(Permissions.ALL.getPermission(),
        liteJsonImporter.getPermissionBitMap(new JSONArray(ImmutableList.of("all"))));
    Assert.assertEquals(Permissions.ALL.getPermission(),
        liteJsonImporter.getPermissionBitMap(new JSONArray(ImmutableList.of("manage"))));
    Assert
        .assertEquals(Permissions.CAN_ANYTHING.getPermission(), liteJsonImporter
            .getPermissionBitMap(new JSONArray(ImmutableList.of("anything"))));
    Assert.assertEquals(Permissions.CAN_ANYTHING_ACL.getPermission(), liteJsonImporter
        .getPermissionBitMap(new JSONArray(ImmutableList.of("anything-acl"))));
    Assert.assertEquals(
        Permissions.CAN_READ.combine(Permissions.CAN_WRITE)
            .combine(Permissions.CAN_DELETE).getPermission(), liteJsonImporter
            .getPermissionBitMap(new JSONArray(ImmutableList
                .of("read", "write", "delete"))));
  }
  
  
  @Test
  public void testOperation() {
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    Assert.assertEquals(Operation.OP_OR, liteJsonImporter.getOperation("or"));
    Assert.assertEquals(Operation.OP_AND, liteJsonImporter.getOperation("and"));
    Assert.assertEquals(Operation.OP_XOR, liteJsonImporter.getOperation("xor"));
    Assert.assertEquals(Operation.OP_NOT, liteJsonImporter.getOperation("not"));
    Assert.assertEquals(Operation.OP_DEL, liteJsonImporter.getOperation("del"));
    Assert.assertEquals(Operation.OP_REPLACE, liteJsonImporter.getOperation("replace"));
    Assert.assertEquals(Operation.OP_OR, liteJsonImporter.getOperation("OR"));
  }
  
  @Test
  public void testImportContent() throws ClientPoolException, StorageClientException, AccessDeniedException, JSONException, IOException {
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    Session session = repository.loginAdministrative();
    ContentManager contentManager = session.getContentManager();
    AccessControlManager accessControlManager = session.getAccessControlManager();
    
    for ( String testFile : TEST_FILES ) {
      JSONObject json = new JSONObject(IOUtils.toString(getClass().getClassLoader().getResourceAsStream(testFile)));
      liteJsonImporter.importContent(contentManager, json, testFile, true, true, true, accessControlManager);
    }
  }
  
  /**
   * Verify that the @Ignore suffix of an imported JSON key is respected, when the value assigned
   * to it is an object.
   * 
   * @throws Exception
   */
  @Test
  public void testImportObjectContentWithIgnore() throws Exception {
    doTestIgnoreWithImportFile(TEST_IGNORE_IMPORT_FILE);
  }
  

  /**
   * Verify that the @Ignore suffix of an imported JSON key is respected, when the value assigned
   * to it is a primitive.
   * 
   * @throws Exception
   */
  @Test
  public void testImportPrimitiveContentWithIgnore() throws Exception {
    doTestIgnoreWithImportFile(TEST_IGNORE_IMPORT_PRIM_FILE);
  }
  

  /**
   * Verify that the @Ignore suffix of an imported JSON key is respected, when the value assigned
   * to it is an array.
   * 
   * @throws Exception
   */
  @Test
  public void testImportArrayContentWithIgnore() throws Exception {
    doTestIgnoreWithImportFile(TEST_IGNORE_IMPORT_ARRAY_FILE);
  }
  
  /**
   * Validate that the import file does not wipe away the
   * {@code id3973361/comments/message/inbox/7a050dcb7ec347ff509103f2b5f08ddcf6cbb203} node,
   * as this should be specified as @Ignore in the specified import file.
   * 
   * @param importFile
   * @throws Exception
   */
  private void doTestIgnoreWithImportFile(String importFile) throws Exception {
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    Session session = repository.loginAdministrative();
    ContentManager cm = session.getContentManager();
    AccessControlManager am = session.getAccessControlManager();
    
    //first load the tree that will be replaced
    JSONObject json = new JSONObject(IOUtils.toString(getClass().getClassLoader()
        .getResourceAsStream(TEST_IGNORE_BASE_FILE)));
    liteJsonImporter.importContent(cm, json, TEST_IGNORE_BASE_FILE, true, true, true, am);
    
    //now import the content ontop of it (do not enable removeTree, @Ignore is not safe from that)
    json = new JSONObject(IOUtils.toString(getClass().getClassLoader().getResourceAsStream(importFile)));
    liteJsonImporter.importContent(cm, json, TEST_IGNORE_BASE_FILE, true, true, false, false, am, true);
    
    String messagePath = String.format("%s/%s", TEST_IGNORE_BASE_FILE,
        "id3973361/comments/message/inbox/7a050dcb7ec347ff509103f2b5f08ddcf6cbb203");
    
    Assert.assertTrue(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE, "id3973361")));
    Assert.assertTrue(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE,
        "id3973361/comments")));
    Assert.assertTrue(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE,
        "id3973361/comments/message")));
    Assert.assertTrue(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE,
        "id3973361/comments/message/inbox")));
    
    // this one verifies that the message beneath "inbox", which should have been ignored, still exists
    Assert.assertTrue(cm.exists(messagePath));
    
    // verify that the import wasn't a complete flop, by ensuring other things were deleted
    Assert.assertFalse(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE, "rows")));
    Assert.assertFalse(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE, "editing")));
    Assert.assertFalse(cm.exists(String.format("%s/%s", TEST_IGNORE_BASE_FILE, "editor")));
    
    // spot-check message properties
    Content message = cm.get(messagePath);
    Assert.assertEquals("id3973361", message.getProperty("sakai:marker"));
    Assert.assertEquals("another comment!", message.getProperty("sakai:body"));
  }

  @Test
  public void testSlingTypeHint() throws Exception {
    JSONObject json = new JSONObject("{\"beAwesome\":true, \"beAwesome@TypeHint\":\"Boolean\"}");
    LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
    Session session = repository.loginAdministrative();
    ContentManager contentManager = session.getContentManager();
    AccessControlManager accessControlManager = session.getAccessControlManager();


    String awesomePath = "/p/awesome-hint";
    liteJsonImporter.importContent(contentManager, json, awesomePath, true, true, false, true, accessControlManager, Boolean.FALSE);
    Content content = contentManager.get(awesomePath);
    final String beAwesome = "beAwesome";
    Object awesomeProperty = content.getProperty(beAwesome);
    Assert.assertTrue(awesomeProperty instanceof Boolean);
    Assert.assertEquals(Boolean.TRUE, content.getProperty(beAwesome));
  }
}

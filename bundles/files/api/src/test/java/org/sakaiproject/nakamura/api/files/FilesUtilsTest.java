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
package org.sakaiproject.nakamura.api.files;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FileUtils.resolveNode;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.testing.jcr.MockNode;
import org.apache.sling.commons.testing.jcr.MockProperty;
import org.apache.sling.commons.testing.jcr.MockPropertyIterator;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.NodeType;

/**
 *
 */
public class FilesUtilsTest {

  @Test
  public void testWriteFileNode() throws JSONException, RepositoryException,
  UnsupportedEncodingException, IOException {
    Session session = mock(Session.class);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    JSONWriter write = new JSONWriter(w);

    Node node = createFileNode();

    FileUtils.writeFileNode(node, session, write);

    w.flush();
    String s = baos.toString("UTF-8");
    JSONObject j = new JSONObject(s);

    assertFileNodeInfo(j);
  }

  /**
   * This test covers all FileUtils.writeFileNode methods that take Content as one of the parameter
   * 
   * @throws JSONException
   * @throws StorageClientException
   * @throws RepositoryException
   * @throws IOException
   * @throws UnsupportedEncodingException
   */
  @Test
  public void testWriteFileNode2() throws JSONException, RepositoryException,
  UnsupportedEncodingException, IOException, StorageClientException {

    org.sakaiproject.nakamura.api.lite.Session session = mock(org.sakaiproject.nakamura.api.lite.Session.class);
    AccessControlManager accessControlManager = mock(AccessControlManager.class);
    when(session.getAccessControlManager()).thenReturn(accessControlManager);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    JSONWriter write = new JSONWriter(w);
    Content content = new Content("/path/to/file.doc", ImmutableMap.of("jcr:name", (Object)"file.doc", "jcr:path", "/path/to/file.doc"));
    try {
      FileUtils.writeFileNode(content, session, write);
    } catch (StorageClientException e) {
      fail("No exception should be thrown");
    }
    w.flush();
    String s = baos.toString("UTF-8");
    JSONObject j = new JSONObject(s);
    assertFileNodeInfo2(j);
  }

  /**
   * @throws JSONException
   *
   */
  private void assertFileNodeInfo(JSONObject j) throws JSONException {
    assertEquals("/path/to/file.doc", j.getString("jcr:path"));
    assertEquals("file.doc", j.getString("jcr:name"));
    assertEquals("bar", j.getString("foo"));
    assertEquals("text/plain", j.getString("jcr:mimeType"));
    assertEquals(12345, j.getLong("jcr:data"));
  }

  /**
   * Assertion method for testWriteFileNode2() and testWriteLinkNode2() 
   * 
   * @throws JSONException
   */
  private void assertFileNodeInfo2(JSONObject j) throws JSONException {
    assertEquals("file.doc", j.getString("jcr:name"));
    assertEquals("/path/to/file.doc", j.getString("jcr:path"));
    assertEquals(true, j.has("permissions"));
  }

  private Node createFileNode() throws ValueFormatException, RepositoryException {
    Calendar cal = Calendar.getInstance();

    Node contentNode = mock(Node.class);
    Property dateProp = mock(Property.class);
    when(dateProp.getDate()).thenReturn(cal);
    Property lengthProp = mock(Property.class);
    when(lengthProp.getLength()).thenReturn(12345L);

    Property mimetypeProp = mock(Property.class);
    when(mimetypeProp.getString()).thenReturn("text/plain");

    when(contentNode.getProperty(JcrConstants.JCR_LASTMODIFIED)).thenReturn(dateProp);
    when(contentNode.getProperty(JcrConstants.JCR_MIMETYPE)).thenReturn(mimetypeProp);
    when(contentNode.getProperty(JcrConstants.JCR_DATA)).thenReturn(lengthProp);
    when(contentNode.hasProperty(JcrConstants.JCR_DATA)).thenReturn(true);

    Node node = mock(Node.class);

    Property fooProp = new MockProperty("foo");
    fooProp.setValue("bar");
    List<Property> propertyList = new ArrayList<Property>();
    propertyList.add(fooProp);
    MockPropertyIterator propertyIterator = new MockPropertyIterator(
        propertyList.iterator());

    when(node.getProperties()).thenReturn(propertyIterator);
    when(node.hasNode("jcr:content")).thenReturn(true);
    when(node.getNode("jcr:content")).thenReturn(contentNode);
    when(node.getPath()).thenReturn("/path/to/file.doc");
    when(node.getName()).thenReturn("file.doc");

    return node;
  }

  @Test
  public void testWriteLinkNode() throws JSONException, RepositoryException, IOException {
    Session session = mock(Session.class);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    JSONWriter write = new JSONWriter(w);

    Node node = new MockNode("/path/to/link");
    node.setProperty(FilesConstants.SAKAI_LINK, "uuid");
    node.setProperty("foo", "bar");
    Node fileNode = createFileNode();
    when(session.getNodeByIdentifier("uuid")).thenReturn(fileNode);

    FileUtils.writeLinkNode(node, session, write);
    w.flush();
    String s = baos.toString("UTF-8");
    JSONObject j = new JSONObject(s);

    assertEquals("/path/to/link", j.getString("jcr:path"));
    assertEquals("bar", j.getString("foo"));
    assertFileNodeInfo(j.getJSONObject("file"));

  }


  /**
   * This test covers all FileUtils.writeLinkNode methods that take Content as one of the parameter
   * 
   * @throws JSONException
   * @throws StorageClientException
   * @throws RepositoryException
   * @throws IOException
   */
  @Test
  public void testWriteLinkNode2() throws JSONException, RepositoryException, IOException, StorageClientException {
    org.sakaiproject.nakamura.api.lite.Session session = mock(org.sakaiproject.nakamura.api.lite.Session.class);
    AccessControlManager accessControlManager = mock(AccessControlManager.class);
    when(session.getAccessControlManager()).thenReturn(accessControlManager);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    JSONWriter write = new JSONWriter(w);
    Content content = new Content("/path/to/file.doc", ImmutableMap.of("jcr:name", (Object)"file.doc", "jcr:path", "/path/to/file.doc"));
    try {
      FileUtils.writeLinkNode(content, session, write);
    } catch (StorageClientException e) {
      fail("No exception should be thrown");
    }
    w.flush();
    String s = baos.toString("UTF-8");
    JSONObject j = new JSONObject(s);
    assertFileNodeInfo2(j);
  }

  /*
  @Test
  public void testIsTag() throws RepositoryException {
    Node node = new MockNode("/path/to/tag");
    node.setProperty(SLING_RESOURCE_TYPE_PROPERTY, FilesConstants.RT_SAKAI_TAG);
    boolean result = FileUtils.isTag(node);
    assertEquals(true, result);

    node.setProperty(SLING_RESOURCE_TYPE_PROPERTY, "foobar");
    result = FileUtils.isTag(node);
    assertEquals(false, result);
  }
   */

  @Test
  public void testCreateLinkNode() throws AccessDeniedException, RepositoryException {

    Node fileNode = createFileNode();
    Session session = mock(Session.class);
    Session adminSession = mock(Session.class);
    SlingRepository slingRepository = mock(SlingRepository.class);
    String linkPath = "/path/to/link";

    when(session.getUserID()).thenReturn("alice");
    when(fileNode.getSession()).thenReturn(session);
    NodeType[] nodeTypes = new NodeType[0];
    when(fileNode.getMixinNodeTypes()).thenReturn(nodeTypes);

    when(session.getItem(fileNode.getPath())).thenReturn(fileNode);
    when(adminSession.getItem(fileNode.getPath())).thenReturn(fileNode);
    when(slingRepository.loginAdministrative(null)).thenReturn(adminSession);
    when(adminSession.hasPendingChanges()).thenReturn(true);
    when(session.hasPendingChanges()).thenReturn(true);

    // link
    Node linkNode = mock(Node.class);
    when(session.itemExists(linkPath)).thenReturn(true);
    when(session.getItem(linkPath)).thenReturn(linkNode);
    NodeType nodeType = mock(NodeType.class);
    when(linkNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:unstructured");

    FileUtils.createLink(fileNode, linkPath, slingRepository);

    verify(fileNode).addMixin(FilesConstants.REQUIRED_MIXIN);
    verify(session).save();
    verify(adminSession).save();
    verify(adminSession).logout();
  }

  @Test
  public void testResolveNode() throws RepositoryException {
    ResourceResolver resourceResolver = mock(ResourceResolver.class);
    Session session = mock(Session.class);
    when(resourceResolver.adaptTo(Session.class)).thenReturn(session);

    // test IllegalArgumentException for null pathOrIdentifier
    try {
      @SuppressWarnings("unused")
      Node node = resolveNode(null, resourceResolver);
      fail("IllegalArgumentException should be thrown for null pathOrIdentifier");
    } catch (IllegalArgumentException e) {
      // expected
    }

    // test IllegalArgumentException for empty pathOrIdentifier
    try {
      @SuppressWarnings("unused")
      Node node = resolveNode("", resourceResolver);
      fail("IllegalArgumentException should be thrown for empty pathOrIdentifier");
    } catch (IllegalArgumentException e) {
      // expected
    }

    // test IllegalArgumentException for null Session
    try {
      @SuppressWarnings("unused")
      Node node = resolveNode("/foo", null);
      fail("Should have thrown an exception for null resource resolver");
    } catch (IllegalArgumentException e) {
      // expected
    }

    // test path not found (i.e. fully qualified path use case)
    when(session.getNode(anyString())).thenThrow(new PathNotFoundException());
    try {
      Node node = resolveNode("/foo/bar", resourceResolver);
      assertNull("Node should resolve to null", node);
    } catch (Throwable e) {
      fail("No exception should be thrown for PathNotFoundException");
    }

    // test item not found (i.e. uuid or poolId use case)
    when(session.getNodeByIdentifier(anyString())).thenThrow(new ItemNotFoundException());
    try {
      Node node = resolveNode("UUID1234", resourceResolver);
      assertNull("Node should resolve to null", node);
    } catch (Throwable e) {
      fail("No exception should be thrown for ItemNotFoundException");
    }

    // test path found fully qualified
    Node fullyQualifiedNode = mock(Node.class, "fullyQualifiedNode");
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Node.class)).thenReturn(fullyQualifiedNode);
    when(resourceResolver.resolve(startsWith("/"))).thenReturn(resource);
    try {
      Node node = resolveNode("/should/exist", resourceResolver);
      assertEquals("Node should resolve to modelNode", node, fullyQualifiedNode);
    } catch (Throwable e) {
      fail("No exception should be thrown");
    }

    // test path found UUID
    Node uuidNode = mock(Node.class, "uuidNode");
    Mockito.reset(session); // TODO This test should be split into more focused methods
    when(session.getNodeByIdentifier("UUID1234")).thenReturn(uuidNode);
    try {
      Node node = resolveNode("UUID1234", resourceResolver);
      assertEquals("Node should resolve to modelNode", node, uuidNode);
    } catch (Throwable e) {
      fail("No exception should be thrown");
    }

    // test path found poolId
    Node poolIdNode = mock(Node.class, "poolIdNode");
    // see CreateContentPoolServlet.generatePoolId() method
    when(session.getNode("/_p/k/dg/dd/nr/poolId1234")).thenReturn(poolIdNode);
    try {
      @SuppressWarnings("unused")
      Node node = resolveNode("poolId1234", resourceResolver);

      // TODO: fix this
      Assert.fail("Pool Nodes cant be tagged at the moment");
    } catch (Throwable e) {
    }
  }

  /**
   * This test covers FileUtils.writeComments method 
   * 
   * @throws JSONException
   * @throws StorageClientException
   * @throws RepositoryException
   * @throws IOException
   */
  @Test
  public void testWriteComments() throws JSONException, RepositoryException,
  UnsupportedEncodingException, IOException {

    org.sakaiproject.nakamura.api.lite.Session session = mock(org.sakaiproject.nakamura.api.lite.Session.class);
    ContentManager cm = mock(ContentManager.class);
    Content content = new Content("/path/to/file.doc", ImmutableMap.of("jcr:name", (Object)"file.doc", "jcr:path", "/path/to/file.doc"));
    Content comments = new Content("/path/to/file.doc/comments", ImmutableMap.of("name", (Object)"Tom", "text", "This is a test comment"));
    try {
      when(cm.get("/path/to/file.doc/comments")).thenReturn(comments);
      when(session.getContentManager()).thenReturn(cm);
    } catch (org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException e) {
      fail("No exception should be thrown");
    } catch (StorageClientException e1) {
      fail("No exception should be thrown");
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    JSONWriter write = new JSONWriter(w);

    try {
      write.object();
      FileUtils.writeComments(content, session, write);
    } catch (StorageClientException e) {
      fail("No exception should be thrown for StorageClientException");
    }
    write.endObject();
    w.flush();
    String s = baos.toString("UTF-8");
    JSONObject j = new JSONObject(s);
    JSONObject jc = j.getJSONObject("comments");
    assertEquals("Tom", jc.get("name"));
    assertEquals("This is a test comment", jc.get("text"));
  }

}

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
package org.sakaiproject.nakamura.basiclti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;

import java.util.Dictionary;

@RunWith(MockitoJUnitRunner.class)
public class LiteDefaultContextIdResolverTest {
  @Mock
  Content node;
  @Mock
  Session session;
  @Mock
  AuthorizableManager authManager;
  @Mock
  Group group;
  @Mock
  ComponentContext context;
  @Mock
  Dictionary props;

  LiteDefaultContextIdResolver liteDefaultContextIdResolver;
  final String groupId = "groupId1234";
  final String sakaiCleSiteProp = "sakai:cle-site";
  final String nodePath = "/some/path/to/node";

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    liteDefaultContextIdResolver = new LiteDefaultContextIdResolver();
    when(session.getAuthorizableManager()).thenReturn(authManager);
    when(node.getPath()).thenReturn(nodePath);
    when(authManager.findAuthorizable(groupId)).thenReturn(group);
    when(context.getProperties()).thenReturn(props);
    when(props.get(LiteDefaultContextIdResolver.LTI_CONTEXT_ID)).thenReturn(
        "lti_context_id");
  }

  /**
   * Test the most common use case where launch is within a world and the world id is used
   * as context.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdCommonCase() throws Exception {
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("The worldId should be used as context", groupId, testContext);
  }

  /**
   * In this case the world has a special property that is used for the contextId.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialProperty1() throws Exception {
    final String someLtiContextId = "some_unique_lti_context_id";
    when(group.hasProperty(liteDefaultContextIdResolver.key)).thenReturn(true);
    when(group.getProperty(liteDefaultContextIdResolver.key))
        .thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("someLtiContextId should be used as context", someLtiContextId,
        testContext);
  }

  /**
   * In this case the world has an alternate, special property that is used for the
   * contextId.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialProperty2() throws Exception {
    final String someLtiContextId = "some_unique_lti_context_id";
    when(group.hasProperty(sakaiCleSiteProp)).thenReturn(true);
    when(group.getProperty(sakaiCleSiteProp)).thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("someLtiContextId should be used as context", someLtiContextId,
        testContext);
  }

  @Test
  public void testResolveContextIdNodeHasContextId() throws Exception {
    final String someLtiContextId = "some_unique_lti_context_id";
    when(node.hasProperty(liteDefaultContextIdResolver.key)).thenReturn(true);
    when(node.getProperty(liteDefaultContextIdResolver.key)).thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("someLtiContextId should be used as context", someLtiContextId,
        testContext);
  }

  /**
   * Pass a null Content node
   * 
   * @throws Exception
   */
  @Test(expected = IllegalArgumentException.class)
  public void testResolveContextIdNullContent() throws Exception {
    liteDefaultContextIdResolver.resolveContextId(null, groupId, session);
  }

  /**
   * Pass a null Session
   * 
   * @throws Exception
   */
  @Test(expected = IllegalArgumentException.class)
  public void testResolveContextIdNullSession() throws Exception {
    liteDefaultContextIdResolver.resolveContextId(node, groupId, null);
  }

  /**
   * Support old client semantics before groupId was passed from client. i.e. groupId ==
   * null
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdNullGroupId() throws Exception {
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node, null,
        session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("The nodePath should be used as context", nodePath, testContext);
  }

  /**
   * Test the access denied use case where someone passes in a groupId for which they are
   * not a member.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdAccessDeniedToGroup() throws Exception {
    when(authManager.findAuthorizable(groupId)).thenReturn(null);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("The nodePath should be used as context", nodePath, testContext);
  }

  /**
   * Test edge case where the group has a property but it is null.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialPropertyThatIsNull() throws Exception {
    final String someLtiContextId = null;
    when(group.hasProperty(liteDefaultContextIdResolver.key)).thenReturn(true);
    when(group.getProperty(liteDefaultContextIdResolver.key))
        .thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("groupId should be used as context", groupId, testContext);
  }

  /**
   * Test edge case where the group has a property but it is an empty string.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialPropertyThatIsEmpty() throws Exception {
    final String someLtiContextId = "";
    when(group.hasProperty(liteDefaultContextIdResolver.key)).thenReturn(true);
    when(group.getProperty(liteDefaultContextIdResolver.key))
        .thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("groupId should be used as context", groupId, testContext);
  }

  /**
   * Test edge case where the group has a property but it is null.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialProperty2ThatIsNull() throws Exception {
    final String someLtiContextId = null;
    when(group.hasProperty(sakaiCleSiteProp)).thenReturn(true);
    when(group.getProperty(sakaiCleSiteProp)).thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("groupId should be used as context", groupId, testContext);
  }

  /**
   * Test edge case where the group has a property but it is an empty string.
   * 
   * @throws Exception
   */
  @Test
  public void testResolveContextIdWorldHasSpecialProperty2ThatIsEmpty() throws Exception {
    final String someLtiContextId = "";
    when(group.hasProperty(sakaiCleSiteProp)).thenReturn(true);
    when(group.getProperty(sakaiCleSiteProp)).thenReturn(someLtiContextId);
    final String testContext = liteDefaultContextIdResolver.resolveContextId(node,
        groupId, session);
    assertNotNull("Should always return a contextId", testContext);
    assertEquals("groupId should be used as context", groupId, testContext);
  }

  /**
   * Test case where null is returned and default key should remain as setting.
   */
  @Test
  public void testActivateNull() {
    final String key = liteDefaultContextIdResolver.key;
    when(props.get(LiteDefaultContextIdResolver.LTI_CONTEXT_ID)).thenReturn(null);
    liteDefaultContextIdResolver.activate(context);
    assertEquals("key should be equal", key, liteDefaultContextIdResolver.key);
  }

  /**
   * Test case where we are changing the default value to something different.
   */
  @Test
  public void testActivateNewValue() {
    final String newKey = "newKey";
    when(props.get(LiteDefaultContextIdResolver.LTI_CONTEXT_ID)).thenReturn(newKey);
    liteDefaultContextIdResolver.activate(context);
    assertEquals("key should be equal", newKey, liteDefaultContextIdResolver.key);
  }

  /**
   * Test for edge case where an empty string is returned and default key should remain as
   * setting.
   */
  @Test
  public void testActivateEmptyString() {
    final String key = liteDefaultContextIdResolver.key;
    final String emptyString = "";
    when(props.get(LiteDefaultContextIdResolver.LTI_CONTEXT_ID)).thenReturn(emptyString);
    liteDefaultContextIdResolver.activate(context);
    assertEquals("key should be equal", key, liteDefaultContextIdResolver.key);
  }
}

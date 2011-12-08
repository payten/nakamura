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
package org.sakaiproject.nakamura.message.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.locking.LockManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.message.listener.LiteMessageRoutesImpl;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 *
 */
public class LiteInternalMessageHandlerTest {

  private LiteInternalMessageHandler handler;
  private LiteMessagingService messagingService;
  private LockManager lockManager;
  private Repository repository;
  private Session session;
  private ContentManager cm;
  private String groupName = "g_group1";

  @Before
  public void setUp() throws Exception {
    messagingService = mock(LiteMessagingService.class);
    repository = mock(Repository.class);
    lockManager = mock(LockManager.class);
    handler = new LiteInternalMessageHandler();
    handler.messagingService = messagingService;
    handler.slingRepository = repository;
    handler.lockManager = lockManager;
    session = mock(Session.class);
    cm = mock(ContentManager.class);
    when(session.getContentManager()).thenReturn(cm);
  }

  @Test
  public void testHandle() throws Exception {

    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getRemoteUser()).thenReturn("admin");
    Authorizable admin = createAuthorizable("admin", false);
    Group group = (Group) createAuthorizable(groupName, true);
    AuthorizableManager am = createAuthManager(null, admin, group);
    ResourceResolver resolver = mock(ResourceResolver.class);
    when(session.getAuthorizableManager()).thenReturn(am);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(Session.class)).thenReturn(session);


    String path = "/path/to/msg";
    String newPath = "/path/to/new/msg";
    String to = "internal:admin";

    when(messagingService.getFullPathToStore(isA(String.class), isA(Session.class))).thenReturn(path);
    testMessage(path, newPath, to);

    //Set up group members:
    String[] members = { admin.getId() };

    registerAuthorizable(group, am, groupName);
    when(group.isGroup()).thenReturn(true);
    when(group.getMembers()).thenReturn(members);

    path = "/path/to/msg2";
    newPath = "/path/to/new/msg2";
    to = "internal:" + groupName;
    testMessage(path, newPath, to);

  }

  private void testMessage(String path, String newPath, String to) throws Exception {
 // Original message created to send
    Map<String, Object> props = ImmutableMap.of(MessageConstants.PROP_SAKAI_TO,
        (Object) to, MessageConstants.PROP_SAKAI_ID, "foo");
    Content originalMessage = new Content(path, props);

    Map<String, Object> newProps = ImmutableMap.of(MessageConstants.PROP_SAKAI_READ, (Object) Boolean.FALSE,
        MessageConstants.PROP_SAKAI_MESSAGEBOX, MessageConstants.BOX_INBOX,
        MessageConstants.PROP_SAKAI_SENDSTATE, MessageConstants.STATE_NOTIFIED);
    Content newNode = new Content(newPath, newProps);
    when(cm.get(newPath)).thenReturn(newNode);

    when(repository.loginAdministrative()).thenReturn(session);

    when(messagingService.getFullPathToMessage("admin", "foo", session)).thenReturn(
        newPath);

    MessageRoutes routes = new LiteMessageRoutesImpl(originalMessage);

    handler.send(routes, null, originalMessage);

    assertEquals(false, (Boolean) newNode.getProperty(MessageConstants.PROP_SAKAI_READ));
    assertEquals(MessageConstants.BOX_INBOX, newNode.getProperty(
        MessageConstants.PROP_SAKAI_MESSAGEBOX));
    assertEquals(MessageConstants.STATE_NOTIFIED, newNode.getProperty(
        MessageConstants.PROP_SAKAI_SENDSTATE));
  }

  private void registerAuthorizable(Authorizable authorizable, AuthorizableManager am, String name)
  throws Exception {
    String hashedPath = "/"+name.substring(0,1)+"/"+name.substring(0,2)+"/"+name;
    when(authorizable.hasProperty("path")).thenReturn(true);
    when(authorizable.getProperty("path")).thenReturn(hashedPath);
    when(authorizable.getId()).thenReturn(name);
    when(am.findAuthorizable(name)).thenReturn(authorizable);
  }

  protected Authorizable createAuthorizable(String id, boolean isGroup)
  throws Exception {
    Authorizable au;
    if(isGroup)
        au = mock(Group.class);
    else
        au = mock(Authorizable.class);
    when(au.getId()).thenReturn(id);
    when(au.isGroup()).thenReturn(isGroup);
    String hashedPath = "/"+id.substring(0,1)+"/"+id.substring(0,2)+"/"+id;
    when(au.hasProperty("path")).thenReturn(true);
    when(au.getProperty("path")).thenReturn(hashedPath);
    return au;
  }

  protected AuthorizableManager createAuthManager(AuthorizableManager am,
      Authorizable... authorizables) throws Exception {
    if (am == null) {
      am = mock(AuthorizableManager.class);
    }
    for (Authorizable au : authorizables) {
      when(am.findAuthorizable(au.getId())).thenReturn(au);
    }
    return am;
  }
}

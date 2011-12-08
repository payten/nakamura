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
package org.sakaiproject.nakamura.discussion;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.locking.LockManager;
import org.sakaiproject.nakamura.api.message.AbstractMessageRoute;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteDiscussionMessageTransportTest {
  private LiteDiscussionMessageTransport transport;
  @Mock
  private LiteMessagingService messagingService;
  @Mock
  private LockManager lockManager;
  @Mock
  private Repository repository;
  @Mock
  private Session adminSession;
  @Mock
  private EventAdmin eventAdmin;
  @Mock
  private ContentManager cm;

  @Before
  public void setUp() throws Exception {
    transport = new LiteDiscussionMessageTransport();
    transport.activateTesting();
    
    when(repository.loginAdministrative()).thenReturn(adminSession);

    transport.messagingService = messagingService;
    transport.lockManager = lockManager;
    transport.contentRepository = repository;
    transport.eventAdmin = eventAdmin;
    
    when(adminSession.getContentManager()).thenReturn(cm);
  }

  // PowerMock should allow us to mock static methods.
  // As of 2010-02-11 It gave an IncompatibleClassChangeError

  @Test
  public void testSend() throws Exception {
    Map<String, Object> props = ImmutableMap.of(MessageConstants.PROP_SAKAI_ID,
        (Object) "a1b2c3d4e5f6", MessageConstants.PROP_SAKAI_FROM, "johndoe");
    Content node = new Content("/path/to/msg", props);
    when(messagingService.getFullPathToMessage("s-site", "a1b2c3d4e5f6", adminSession))
        .thenReturn("/sites/site/store/a1b2c3d4e5f6");
    when(messagingService.getFullPathToStore("s-site", adminSession)).thenReturn(
        "/sites/site/store");
    when(lockManager.waitForLock("/sites/site/store/a1b2c3d4e5f6")).thenReturn(null);
    Content messageNode = new Content("/sites/site/store/a1b2c3d4e5f6", null);
    when(cm.get("/sites/site/store/a1b2c3d4e5f6")).thenReturn(messageNode);

    MockMessageRoutes routes = new MockMessageRoutes();
    MessageRoute route = new AbstractMessageRoute("discussion:s-site") {
    };
    routes.add(route);
    
    lockManager.clearLocks();

    transport.send(routes, null, node);

    ArgumentCaptor<Content> newMessage = ArgumentCaptor.forClass(Content.class);
    verify(cm).update(newMessage.capture());
    messageNode = newMessage.getValue();
    assertEquals("notified",
        messageNode.getProperty(MessageConstants.PROP_SAKAI_SENDSTATE));
    assertEquals("inbox", messageNode.getProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX));
    assertEquals("s-site", messageNode.getProperty(MessageConstants.PROP_SAKAI_TO));
  }

  class MockMessageRoutes extends ArrayList<MessageRoute> implements MessageRoutes {
    private static final long serialVersionUID = 6908624167365901970L;
  }
}

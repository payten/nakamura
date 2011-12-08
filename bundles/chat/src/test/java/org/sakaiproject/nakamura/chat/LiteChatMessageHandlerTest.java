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
package org.sakaiproject.nakamura.chat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.api.message.AbstractMessageRoute;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.memory.MapCacheImpl;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteChatMessageHandlerTest {
  private LiteChatMessageHandler handler;
  @Mock
  private Repository repository;
  @Mock
  private LiteMessagingService messagingService;
  @Mock
  private Session adminSession;
  @Mock
  private ContentManager cm;
  @Mock
  private CacheManagerService cacheManagerService;
  private ChatManagerServiceImpl chatManagerService;
  private Cache<Object> chatCache;

  private String rcpt = "johndoe";
  private String messageId = "12345";
  private String pathToMessage = "/_user/message/johndoe/12345";
  private String fromPath = "/_user/message/jack/12345";

  @Before
  public void setUp() throws Exception {
    when(repository.loginAdministrative()).thenReturn(adminSession);

    when(messagingService.getFullPathToMessage(rcpt, messageId, adminSession))
        .thenReturn(pathToMessage);

    // chat manager service mocks
    chatCache = new MapCacheImpl<Object>("testchat",CacheScope.INSTANCE);
    when(cacheManagerService.getCache("chat", CacheScope.CLUSTERREPLICATED))
        .thenReturn(chatCache);
    chatManagerService = new ChatManagerServiceImpl();
    chatManagerService.bindCacheManagerService(cacheManagerService);

    handler = new LiteChatMessageHandler();
    handler.chatManagerService = chatManagerService;
    handler.messagingService = messagingService;
    handler.contentRepository = repository;

    when(adminSession.getContentManager()).thenReturn(cm);
  }

  @Test
  public void testHandle() throws Exception {
    Long time = System.currentTimeMillis() - 10000;
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(time);
    Map<String, Object> props = ImmutableMap.of(MessageConstants.PROP_SAKAI_ID, (Object) messageId,
        MessageConstants.PROP_SAKAI_CREATED, cal,
        MessageConstants.PROP_SAKAI_FROM, "jack");
    Content originalMessage = new Content(fromPath, props);

    Content messageNode = new Content(pathToMessage, null);

    Content msgStore = new Content("", null);
    when(cm.get("/_user/message/johndoe")).thenReturn(msgStore);
    when(cm.get(pathToMessage)).thenReturn(messageNode);

    MessageRoutes routes = new MessageRoutesTest();
    MessageRoute route = new AbstractMessageRoute("chat:" + rcpt) {
    };
    routes.add(route);

    handler.send(routes, null, originalMessage);

    assertEquals(time, chatManagerService.get("jack"));
    assertEquals(time, chatManagerService.get("johndoe"));
  }

  public class MessageRoutesTest extends ArrayList<MessageRoute> implements
      MessageRoutes {
    private static final long serialVersionUID = 981738821907909267L;
  }

}

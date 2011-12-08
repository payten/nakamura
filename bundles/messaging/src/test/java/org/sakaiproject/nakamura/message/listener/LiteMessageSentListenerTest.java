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
package org.sakaiproject.nakamura.message.listener;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.LiteMessageRouterManager;
import org.sakaiproject.nakamura.api.message.LiteMessageTransport;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoutes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

public class LiteMessageSentListenerTest {
  private LiteMessageSentListener msl;
  private LiteMessageRouterManager messageRouterManager;
  private Repository repository;
  private static final String PATH = "/foo/bar";
  private Session session;
  private ContentManager cm;

  @Before
  public void setup() throws Exception {
    Map<String, Object> props = ImmutableMap.of(MessageConstants.PROP_SAKAI_TO, (Object) ":admin");
    Content node = new Content("", props);

    messageRouterManager = mock(LiteMessageRouterManager.class);
    when(messageRouterManager.getMessageRouting(isA(Content.class))).thenReturn(
        new LiteMessageRoutesImpl(node));


    session = mock(Session.class);
    cm = mock(ContentManager.class);
    when(session.getContentManager()).thenReturn(cm);

    Map<String, Object> msgProps = ImmutableMap.of(
        JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
        (Object) MessageConstants.SAKAI_MESSAGE_RT);
    Content msgNode = new Content(PATH, msgProps);

    when(cm.get(PATH)).thenReturn(msgNode);

    repository = mock(Repository.class);
    when(repository.loginAdministrative()).thenReturn(session);

    msl = new LiteMessageSentListener(messageRouterManager, repository);
  }

  @Test
  public void testHandleEvent() throws Exception {

    Map<String, String> eventProps = Maps.newHashMap();
    eventProps.put(MessageConstants.EVENT_LOCATION, PATH);
    Event event = new Event("myTopic", eventProps);
    Map<String, Object> props = ImmutableMap.of(
        JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
        (Object) MessageConstants.SAKAI_MESSAGE_RT);
    Content message = new Content(PATH, props);
    when(cm.get(PATH)).thenReturn(message);

    LiteMessageTransport transport = mock(LiteMessageTransport.class);

    msl.addTransport(transport);
    msl.handleEvent(event);
    msl.removeTransport(transport);

    verify(transport).send(isA(MessageRoutes.class), eq(event), eq(message));
  }
}

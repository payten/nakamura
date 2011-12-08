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
package org.sakaiproject.nakamura.message.search;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.message.LiteMessagingServiceImpl;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class MessageSparseSearchPropertyProviderTest {

  @Test
  public void testProperties() throws Exception {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);

    ResourceResolver resolver = mock(ResourceResolver.class);
    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));
    Session session = mock(Session.class);
    
    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    when(request.getRemoteUser()).thenReturn("admin");

    AuthorizableManager am = mock(AuthorizableManager.class);
    Authorizable au = mock(Authorizable.class);
    when(am.findAuthorizable("admin")).thenReturn(au);
    when(session.getAuthorizableManager()).thenReturn(am);

    // Special requests
    RequestParameter fromParam = mock(RequestParameter.class);
    when(fromParam.getString()).thenReturn("usera,userb");
    when(request.getRequestParameter("_from")).thenReturn(fromParam);

    Resource mockResource = mock(Resource.class);
    when(mockResource.getResourceType()).thenReturn("sakai/solr-search");
    when(request.getResource()).thenReturn(mockResource);

    Map<String, String> pMap = new HashMap<String, String>();

    MessageSparseSearchPropertyProvider provider = new MessageSparseSearchPropertyProvider();
    LiteMessagingService messagingService = new LiteMessagingServiceImpl();
    provider.messagingService = messagingService;
    provider.loadUserProperties(request, pMap);
    provider.messagingService = null;

    assertEquals(
        ClientUtils.escapeQueryChars(LitePersonalUtils.PATH_AUTHORIZABLE
            + "admin/message/"), pMap.get(MessageConstants.SEARCH_PROP_MESSAGESTORE));

    assertEquals("from:(\"usera\" OR \"userb\")", pMap.get("_from"));
  }

}

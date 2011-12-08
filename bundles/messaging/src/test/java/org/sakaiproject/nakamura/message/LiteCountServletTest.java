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
package org.sakaiproject.nakamura.message;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;

import com.google.common.collect.Lists;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 */
public class LiteCountServletTest {

  private LiteCountServlet servlet;
  private LiteMessagingService messagingService;
  private SolrSearchServiceFactory searchFactory;

  @Before
  public void setUp() {
    servlet = new LiteCountServlet();
    messagingService = mock(LiteMessagingService.class);
    searchFactory = mock(SolrSearchServiceFactory.class);

    servlet.messagingService = messagingService;
    servlet.searchServiceFactory = searchFactory;
  }

  @After
  public void tearDown() {
    servlet.messagingService = messagingService;
  }

  @Test
  public void testParams() throws Exception {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter write = new PrintWriter(baos);
    when(response.getWriter()).thenReturn(write);

    // Request stuff
    RequestParameter groupParam = mock(RequestParameter.class);
    when(groupParam.getString()).thenReturn("foo");
    when(request.getRemoteUser()).thenReturn("admin");
    when(request.getRequestParameter("groupedby")).thenReturn(groupParam);

    // Results
    Map<String, Collection<Object>> props = new HashMap<String, Collection<Object>>();
    props.put("foo", (Collection<Object>) Lists.newArrayList((Object) "a"));
    Map<String, Collection<Object>> propsC = new HashMap<String, Collection<Object>>();
    propsC.put("foo", (Collection<Object>) Lists.newArrayList((Object) "c"));

    // Session & search
    ResourceResolver rr = mock(ResourceResolver.class);
    when(request.getResourceResolver()).thenReturn(rr);
    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    Session session = mock(Session.class);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    
    // Node
    Content node = new Content("/_user/message.count.json", null);
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Content.class)).thenReturn(node);
    when(request.getResource()).thenReturn(resource);

    when(messagingService.getFullPathToStore("admin", session)).thenReturn(
        "/path/to/store");

    Result r1 = mock(Result.class);
    when(r1.getPath()).thenReturn("/path/to/msgA");
    when(r1.getProperties()).thenReturn(props);
    Result r2 = mock(Result.class);
    when(r2.getPath()).thenReturn("/path/to/msgB");
    when(r2.getProperties()).thenReturn(props);
    Result r3 = mock(Result.class);
    when(r3.getPath()).thenReturn("/path/to/msgC");
    when(r3.getProperties()).thenReturn(propsC);
    Iterator<Result> results = Lists.newArrayList(r1, r2, r3).iterator();

    SolrSearchResultSet resultSet = mock(SolrSearchResultSet.class);
    when(searchFactory.getSearchResultSet(isA(SlingHttpServletRequest.class), isA(Query.class), anyBoolean())).thenReturn(resultSet);
    when(resultSet.getResultSetIterator()).thenReturn(results);
    servlet.doGet(request, response);

    write.flush();
    String s = baos.toString("UTF-8");
    JSONObject o = new JSONObject(s);

    JSONArray arr = o.getJSONArray("count");
    assertEquals(1, arr.length());
    assertEquals("null", arr.getJSONObject(0).getString("group"));
    assertEquals("3", arr.getJSONObject(0).getString("count"));

  }
}

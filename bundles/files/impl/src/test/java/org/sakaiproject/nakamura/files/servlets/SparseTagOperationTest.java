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
package org.sakaiproject.nakamura.files.servlets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.common.collect.ImmutableMap;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.SparseContentResource;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.Arrays;

/**
 *
 */
public class SparseTagOperationTest {

  SparseTagOperation operation;
  ResourceResolver resolver;
  javax.jcr.Session jcrSession;
  SlingHttpServletRequest request;
  HtmlResponse response;
  Session session;
  Session adminSession;
  ContentManager contentManager;
  Repository repository;

  @Before
  public void setUp() throws Exception {
    repository = (Repository) new BaseMemoryRepository().getRepository();
    session = repository.login();
    adminSession = repository.loginAdministrative();
    contentManager = adminSession.getContentManager();

    operation = new SparseTagOperation();
    operation.eventAdmin = mock(EventAdmin.class);
    resolver = mock(ResourceResolver.class);
    jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));
    request = mock(SlingHttpServletRequest.class);
    response = new HtmlResponse();
    repository = mock(Repository.class);

    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
 }

  @Test
  public void testAnonRequest() throws Exception {
    Resource resource = mock(Resource.class);
    when(request.getResource()).thenReturn(resource);
    when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
    operation.doRun(request, response, null, null, "/blah/blah");
    assertEquals(403, response.getStatusCode());
  }

  @Test
  public void testMissingResource() throws Exception {
    // This shouldn't really happen, but hey!
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Content.class)).thenReturn(null);
    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, null, null, "/blah/blah");

    assertEquals(400, response.getStatusCode());
  }

  @Test
  public void testMissingParams() throws Exception {
    Resource resource = mock(Resource.class);
    Content content = new Content("/bla/bla", null);
    when(resource.adaptTo(Content.class)).thenReturn(content);

    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getRequestParameter("key")).thenReturn(null);
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, null, null, "/blah/blah");

    assertEquals(400, response.getStatusCode());
  }

  @Test
  public void testTagWithNewTag() throws Exception {
      Resource resource = mock(Resource.class);
      when(resource.getPath()).thenReturn("/bla/bla");

      Content content = new Content("/bla/bla", null);

      contentManager.update(content);

      when(resource.adaptTo(Content.class)).thenReturn(content);
      when(request.getResource()).thenReturn(resource);
      when(request.getResourceResolver()).thenReturn(resolver);
      when(request.getParameterValues("key")).thenReturn(new String[] { "/tags/foo" });
      when(request.getParameter(":operation")).thenReturn("tag");
      when(request.getRemoteUser()).thenReturn("john");

      operation.doRun(request, response, contentManager, null, "/bla/bla");

      assertEquals(200, response.getStatusCode());

      Content
          tagResult = contentManager.get("/tags/foo"),
          result = contentManager.get("/bla/bla");

      assertEquals ("foo", tagResult.getProperty("sakai:tag-name"));
      assertEquals ("sakai/tag", tagResult.getProperty("sling:resourceType"));

      String tags[] = (String[]) result.getProperty("sakai:tags");

      Arrays.sort(tags);
      assertTrue (Arrays.binarySearch(tags, "foo") > -1);
  }

    @Test
    public void testTagWithExistingTag() throws Exception {
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn("/bla/bla");

        Content content = new Content("/bla/bla", null);
        contentManager.update(content);

        ImmutableMap.Builder
            builder = ImmutableMap.builder();

        builder.put("sakai:tag-name", "oldtag");
        builder.put("sling:resourceType", "sakai/tag");

        Content oldTag = new Content("/tags/oldtag", builder.build());
        contentManager.update(oldTag);

        when(resolver.getResource("/tags/oldtag")).thenReturn(new SparseContentResource(contentManager.get("/tags/oldtag"), adminSession, resolver, "/tags/oldtag"));

        when(resource.adaptTo(Content.class)).thenReturn(content);
        when(request.getResource()).thenReturn(resource);
        when(request.getResourceResolver()).thenReturn(resolver);
        when(request.getParameterValues("key")).thenReturn(new String[] { "/tags/oldtag" });
        when(request.getParameter(":operation")).thenReturn("tag");
        when(request.getRemoteUser()).thenReturn("john");

        operation.doRun(request, response, contentManager, null, "/bla/bla");

        assertEquals(200, response.getStatusCode());

        Content
            tagResult = contentManager.get("/tags/oldtag"),
            result = contentManager.get("/bla/bla");

        assertEquals ("oldtag", tagResult.getProperty("sakai:tag-name"));

        String tags[] = (String[]) result.getProperty("sakai:tags");

        Arrays.sort(tags);
        assertTrue (Arrays.binarySearch(tags, "oldtag") > -1);
    }

}

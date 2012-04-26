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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_PROFILE_RESOURCE_TYPE;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
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
  Content content;
  Content profile;

  @Before
  public void setUp() throws Exception {
    repository = (Repository) new BaseMemoryRepository().getRepository();
    adminSession = repository.loginAdministrative();

    // set some ACLs so we can write things
    adminSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        "/bla/bla",
        new AclModification[] { new AclModification(AclModification.grantKey("testuser"),
            Permissions.ALL.getPermission(), AclModification.Operation.OP_REPLACE) });
    adminSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        "/tags",
        new AclModification[] {
            new AclModification(AclModification.grantKey("everyone"),
                Permissions.CAN_WRITE.getPermission(),
                AclModification.Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(User.ANON_USER),
                Permissions.CAN_WRITE.getPermission(),
                AclModification.Operation.OP_REPLACE) });
    adminSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        "a:testuser",
        new AclModification[] { new AclModification(AclModification.grantKey("testuser"),
            Permissions.ALL.getPermission(), AclModification.Operation.OP_REPLACE) });

    // create a test user
    AuthorizableManager authMgr = adminSession.getAuthorizableManager();
    if (authMgr.createUser("testuser", "testuser", "test", null)) {
      profile = new Content("a:testuser/public/authprofile", ImmutableMap.<String, Object> of(
          SLING_RESOURCE_TYPE_PROPERTY, USER_PROFILE_RESOURCE_TYPE));
      adminSession.getContentManager().update(profile);
    } else {
      throw new RuntimeException("Can't create test user.");
    }

    // create a test node
    content = new Content("/bla/bla", null);
    adminSession.getContentManager().update(content);

    session = repository.loginAdministrative("testuser");
    contentManager = session.getContentManager();

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
    when(resource.adaptTo(Content.class)).thenReturn(content);

    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, null, null, "/blah/blah");

    assertEquals(400, response.getStatusCode());
  }

  @Test
  public void testTagWithNewTag() throws Exception {
      Resource resource = mock(Resource.class);
      when(resource.getPath()).thenReturn("/bla/bla");

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

      assertEquals ("foo", tagResult.getProperty(SAKAI_TAG_NAME));
      assertEquals ("sakai/tag", tagResult.getProperty(SLING_RESOURCE_TYPE_PROPERTY));

      String tags[] = (String[]) result.getProperty(SAKAI_TAGS);

      Arrays.sort(tags);
      assertTrue (Arrays.binarySearch(tags, "foo") > -1);
  }


  @Test
  public void testTagWithNestedNewTag() throws Exception {
    Resource resource = mock(Resource.class);
    when(resource.getPath()).thenReturn("/bla/bla");

    when(resource.adaptTo(Content.class)).thenReturn(content);
    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getParameterValues("key")).thenReturn(new String[] { "/tags/foo/bar/baz" });
    when(request.getParameter(":operation")).thenReturn("tag");
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, contentManager, null, "/bla/bla");

    assertEquals(200, response.getStatusCode());

    Content
        tagResult = contentManager.get("/tags/foo/bar/baz"),
        result = contentManager.get("/bla/bla");

    assertEquals ("foo/bar/baz", tagResult.getProperty(SAKAI_TAG_NAME));
    assertEquals ("sakai/tag", tagResult.getProperty(SLING_RESOURCE_TYPE_PROPERTY));

    String tags[] = (String[]) result.getProperty(SAKAI_TAGS);

    Arrays.sort(tags);
    assertTrue (Arrays.binarySearch(tags, "foo/bar/baz") > -1);

    // KERN-2621: make sure the ancestors of the new tag are also tags themselves
    Content parent = contentManager.get("/tags/foo/bar");
    assertEquals("foo/bar", parent.getProperty(SAKAI_TAG_NAME));
    assertEquals ("sakai/tag", parent.getProperty(SLING_RESOURCE_TYPE_PROPERTY));

    Content grandParent = contentManager.get("/tags/foo");
    assertEquals ("foo", grandParent.getProperty(SAKAI_TAG_NAME));
    assertEquals ("sakai/tag", grandParent.getProperty(SLING_RESOURCE_TYPE_PROPERTY));

  }

    @Test
    public void testTagWithExistingTag() throws Exception {
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn("/bla/bla");

    ImmutableMap.Builder<String, Object>
            builder = ImmutableMap.builder();

        builder.put("sakai:tag-name", "oldtag");
        builder.put(SLING_RESOURCE_TYPE_PROPERTY, FilesConstants.RT_SAKAI_TAG);

        Content oldTag = new Content("/tags/oldtag", builder.build());
        contentManager.update(oldTag);

        when(resolver.getResource("/tags/oldtag")).thenReturn(new SparseContentResource(contentManager.get("/tags/oldtag"), session, resolver, "/tags/oldtag"));

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

        assertEquals ("oldtag", tagResult.getProperty(SAKAI_TAG_NAME));

        String tags[] = (String[]) result.getProperty(SAKAI_TAGS);

        Arrays.sort(tags);
        assertTrue (Arrays.binarySearch(tags, "oldtag") > -1);
    }

  @Test
  public void testTaggingAuthorizable() throws Exception {
    Resource resource = mock(Resource.class);
    when(resource.getPath()).thenReturn(profile.getPath());

    when(resource.adaptTo(Content.class)).thenReturn(profile);
    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getParameterValues("key")).thenReturn(new String[] { "/tags/foo" });
    when(request.getParameter(":operation")).thenReturn("tag");
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, contentManager, null, "/bla/bla");

    assertEquals(200, response.getStatusCode());

    Content tagResult = contentManager.get("/tags/foo");
    Content result = contentManager.get(profile.getPath());
    Authorizable auth = session.getAuthorizableManager().findAuthorizable("testuser");

    assertEquals ("foo", tagResult.getProperty(SAKAI_TAG_NAME));
    assertEquals ("sakai/tag", tagResult.getProperty(SLING_RESOURCE_TYPE_PROPERTY));

    assertEquals("foo", ((String[]) result.getProperty(SAKAI_TAGS))[0]);
    assertEquals("foo", ((String[]) auth.getProperty(SAKAI_TAGS))[0]);
  }

  @Test
  public void testTaggingWithBadTags() throws Exception {
    Resource resource = mock(Resource.class);
    when(resource.getPath()).thenReturn("/bla/bla");

    when(resource.adaptTo(Content.class)).thenReturn(content);
    when(request.getResource()).thenReturn(resource);
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getParameterValues("key")).thenReturn(new String[] { "/foo", "funky" });
    when(request.getParameter(":operation")).thenReturn("tag");
    when(request.getRemoteUser()).thenReturn("john");

    operation.doRun(request, response, contentManager, null, "/bla/bla");

    assertEquals(404, response.getStatusCode());

    Content tagResult = contentManager.get("/tags/foo");
    contentManager.get("/bla/bla");

    assertNull(tagResult);
  }
}

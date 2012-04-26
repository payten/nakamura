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
package org.sakaiproject.nakamura.activity.search;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.io.JSONWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import java.io.StringWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: zach
 * Date: 9/19/11
 * Time: 3:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class LiteAllActivitiesResultProcessorTest {

  private LiteAllActivitiesResultProcessor processor;
  private SolrSearchServiceFactory solrSearchServiceFactory;
  private Repository repository;
  private javax.jcr.Session jcrSession;
  private Session session;
  private StringWriter stringWriter;
  private SlingHttpServletRequest request;
  private Result result;
  private JSONWriter jsonWriter;
  private String activityPath = "/var/activities/me";
  private String contentPath = "/p/abc123";
  private ContentManager contentManager;

  @Before
  public void setup() throws Exception {
    processor = new LiteAllActivitiesResultProcessor();
    solrSearchServiceFactory = mock(SolrSearchServiceFactory.class);
    processor.searchServiceFactory = solrSearchServiceFactory;
    BasicUserInfoService basicUserInfoService = mock(BasicUserInfoService.class);
    when(basicUserInfoService.getProperties(Matchers.<Authorizable>anyObject())).thenReturn(ImmutableMap.of("firstName", (Object)"Alice", "lastName", "Walter", "email", "alice@example.com"));
    processor.basicUserInfoService = basicUserInfoService;
    jcrSession = mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    stringWriter = new StringWriter();

    repository = new BaseMemoryRepository().getRepository();

    contentManager = repository.loginAdministrative().getContentManager();

    request = mock(SlingHttpServletRequest.class, RETURNS_DEEP_STUBS);
    when(request.getResourceResolver().adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);

    result = mock(Result.class);
    jsonWriter = new ExtendedJSONWriter(stringWriter);

    repository.loginAdministrative().getAuthorizableManager().createUser("alice", "alice", "alice", null);
    session = repository.loginAdministrative("alice");
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    jsonWriter.array();
  }

  @Test
  public void sourceContentMissing() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, "/p/abc123",
      ActivityConstants.PARAM_ACTOR_ID, "alice")));

    when(result.getPath()).thenReturn(activityPath);
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void sourceContentPresent() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "alice")));

    // source content
    contentManager.update(new Content(contentPath, null));

    when(result.getPath()).thenReturn(activityPath);
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void sourceContentAccessDenied() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "alice")));

    // source content
    contentManager.update(new Content(contentPath, null));

    when(result.getPath()).thenReturn(activityPath);
    List<AclModification> aclModifications = Lists.newArrayList();
    AclModification.addAcl(false, Permissions.CAN_READ, "alice", aclModifications);
    repository.loginAdministrative().getAccessControlManager().setAcl(Security.ZONE_CONTENT, "/p/abc123", aclModifications.toArray(new AclModification[aclModifications.size()]));
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void activityPathAccessDenied() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "alice")));

    // source content
    contentManager.update(new Content(contentPath, null));

    when(result.getPath()).thenReturn(activityPath);
    List<AclModification> aclModifications = Lists.newArrayList();
    AclModification.addAcl(false, Permissions.CAN_READ, "alice", aclModifications);
    repository.loginAdministrative().getAccessControlManager().setAcl(Security.ZONE_CONTENT, activityPath, aclModifications.toArray(new AclModification[aclModifications.size()]));
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void activityActorAccessDenied() throws Exception {
    repository.loginAdministrative().getAuthorizableManager().createUser("bob", "bob", "bob", null);
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "bob")));

    // source content
    contentManager.update(new Content(contentPath, null));

    when(result.getPath()).thenReturn(activityPath);
    List<AclModification> aclModifications = Lists.newArrayList();
    AclModification.addAcl(false, Permissions.CAN_READ, "alice", aclModifications);
    repository.loginAdministrative().getAccessControlManager().setAcl(Security.ZONE_AUTHORIZABLES, "bob", aclModifications.toArray(new AclModification[aclModifications.size()]));
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void sourceContentIsGroupHome() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "alice")));

    // source content (group home)
    contentManager.update(new Content(contentPath, ImmutableMap.of("sling:resourceType", (Object)"sakai/group-home")));

    when(result.getPath()).thenReturn(activityPath);
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void sourceContentIsPooledContentWithComment() throws Exception {
    contentManager.update(new Content(activityPath, ImmutableMap.of("sling:resourceType", (Object) ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE,
      ActivityConstants.PARAM_SOURCE, contentPath,
      ActivityConstants.PARAM_ACTOR_ID, "alice",
      "sakai:activityMessage", "CONTENT_ADDED_COMMENT")));

    // source content (pooled content)
    contentManager.update(new Content(contentPath, ImmutableMap.of("sling:resourceType", (Object)"sakai/pooled-content",
      "comment", "Wow, this is a groovy photo. Love the shadows.")));

    when(result.getPath()).thenReturn(activityPath);
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void nullActivityShouldNotCauseBreakage() throws Exception {
    Map<String, Collection<Object>> props = new HashMap<String, Collection<Object>>();
    List<Object> hobbies = Lists.newArrayList();
    hobbies.add("cooking");
    hobbies.add(new Date());
    props.put("hobbies", hobbies);
    when(result.getPath()).thenReturn("/foo/bar/baz");
    when(result.getProperties()).thenReturn(props);
    processor.writeResult(request, jsonWriter, result);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void storageClientError() throws Exception {
    session = mock(Session.class);
    when(session.getContentManager()).thenThrow(new StorageClientException("Something wrong with storage. Shrug."));
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    processor.writeResult(request, jsonWriter, result);
  }

  @Test
  public void callSearchServiceFactory() throws Exception {
    Query query = mock(Query.class);
    processor.getSearchResultSet(request, query);
    verify(solrSearchServiceFactory).getSearchResultSet(request, query);
  }

  @After
  public void outputJson() throws Exception {
    jsonWriter.endArray();
    System.out.println(stringWriter.toString());
  }
}

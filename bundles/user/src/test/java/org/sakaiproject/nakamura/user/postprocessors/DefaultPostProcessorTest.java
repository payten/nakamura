package org.sakaiproject.nakamura.user.postprocessors;

import com.google.common.collect.ImmutableMap;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlets.post.Modification;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

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
@RunWith(MockitoJUnitRunner.class)
public class DefaultPostProcessorTest {

  private Repository repository;
  private Session session;
  private static final int ALL_ACCESS = 28679;

  @Test
  /* suzy is a manager of the group fun-group and is demoting zach
     as a result of being in the ACL for the group, but not in the rep:group-managers property
     zach's permissions must be removed by the post processor
   */
  public void testDemoteAccess() throws Exception {
    // all our dependencies
    repository = (Repository) new BaseMemoryRepository().getRepository();
    final Session adminSession = repository.loginAdministrative();
    final AuthorizableManager adminAuthorizableManager = adminSession.getAuthorizableManager();
    final ContentManager adminContentManager = adminSession.getContentManager();
    final AccessControlManager adminAccessControlManager = adminSession.getAccessControlManager();

    // create a group, suzy is a manager
    assertTrue(adminAuthorizableManager.createGroup("fun-group", "fun-group", ImmutableMap.of(UserConstants.PROP_GROUP_MANAGERS, (Object) new String[]{"suzy"})));
    adminContentManager.update(new Content("a:fun-group", null));
    adminContentManager.update(new Content("a:fun-group/public/authprofile", null));

    // create a couple of users
    assertTrue(adminAuthorizableManager.createUser("suzy", "suzy", "secret", ImmutableMap.of("firstName", (Object) "Suzy", "lastName", "Queue")));
    assertTrue(adminAuthorizableManager.createUser("zach", "zach", "secret", ImmutableMap.of("firstName", (Object) "Zach", "lastName", "Thomas")));

    // grant access to suzy and zach. zach's access will be removed by the post processor
    adminAccessControlManager.setAcl("CO", "a:fun-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AU", "fun-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("CO", "a:fun-group", new AclModification[]{new AclModification("zach@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});

    // set up the parameters we'll pass to the process method of the post processor
    session = repository.loginAdministrative("suzy");
    Authorizable funGroup = session.getAuthorizableManager().findAuthorizable("fun-group");
    SlingHttpServletRequest mockRequest = mock(SlingHttpServletRequest.class);
    Modification mockChange = mock(Modification.class);
    Map<String, Object[]> parameters = new HashMap<String, Object[]>();
    DefaultPostProcessor defaultPostProcessor = new DefaultPostProcessor();

    // call the object under test
    defaultPostProcessor.process(mockRequest, funGroup, session, mockChange, parameters);

    // make sure zach lost access
    Authorizable zach = session.getAuthorizableManager().findAuthorizable("zach");
    assertFalse(adminAccessControlManager.can(zach, "CO", "a:fun-group", Permissions.CAN_ANYTHING));

  }
}

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
package org.sakaiproject.nakamura.user.postprocessors;

import com.google.common.collect.ImmutableMap;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  private Session adminSession;
  private AuthorizableManager adminAuthorizableManager;
  private ContentManager adminContentManager;
  private AccessControlManager adminAccessControlManager;
  private Modification mockChange;
  private DefaultPostProcessor defaultPostProcessor;
  private Map<String, Object[]> parameters;

  private static final int ALL_ACCESS = 28679;

  @Before
  public void setup() throws Exception {
    repository = (Repository) new BaseMemoryRepository().getRepository();
    adminSession = repository.loginAdministrative();
    adminAuthorizableManager = adminSession.getAuthorizableManager();
    adminContentManager = adminSession.getContentManager();
    adminAccessControlManager = adminSession.getAccessControlManager();

    mockChange = mock(Modification.class);
    defaultPostProcessor = new DefaultPostProcessor();
    defaultPostProcessor.repository = repository;
    defaultPostProcessor.eventAdmin = mock(EventAdmin.class);

    parameters = new HashMap<String, Object[]>();
  }

  @Test
  /* suzy is a manager of the group fun-group and is demoting zach
     as a result of being in the ACL for the group, but not in the rep:group-managers property
     zach's permissions must be removed by the post processor
   */
  public void testDemoteAccess() throws Exception {
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
    DefaultPostProcessor defaultPostProcessor = new DefaultPostProcessor();

    // call the object under test
    defaultPostProcessor.process(funGroup, session, mockChange, parameters);

    // make sure zach lost access, but should still be able to read
    Authorizable zach = session.getAuthorizableManager().findAuthorizable("zach");
    assertFalse(adminAccessControlManager.can(zach, "CO", "a:fun-group", Permissions.CAN_WRITE));
    assertTrue(adminAccessControlManager.can(zach, "CO", "a:fun-group", Permissions.CAN_READ));

  }

  @Test
  public void respectLoggedInVisibilityPreference() throws Exception {
    // create a group, suzy is a manager
    assertTrue(adminAuthorizableManager.createGroup("fun-group", "fun-group", ImmutableMap.of(UserConstants.PROP_GROUP_MANAGERS, (Object) new String[]{"suzy"})));

    // create a user to be manager
    assertTrue(adminAuthorizableManager.createUser("suzy", "suzy", "secret", ImmutableMap.of("firstName", (Object) "Suzy", "lastName", "Queue")));
    // grant access to suzy
    adminAccessControlManager.setAcl("CO", "a:fun-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AU", "fun-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AD", "GR", new AclModification[] { new AclModification("suzy@g", Permissions.CAN_WRITE.getPermission(), AclModification.Operation.OP_REPLACE)});

    // set up the parameters we'll pass to the process method of the post processor
    session = repository.loginAdministrative("suzy");
    Authorizable funGroup = session.getAuthorizableManager().findAuthorizable("fun-group");
    when(mockChange.getType()).thenReturn(ModificationType.CREATE);
    defaultPostProcessor.modified(ImmutableMap.of("visibility.preference", "logged_in"));

    // call the object under test with funGroup
    defaultPostProcessor.process(funGroup, session, mockChange, parameters);

    // logged_in means anonymous cannot read, but everyone can
    assertFalse(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(User.ANON_USER), "CO", "a:fun-group", Permissions.CAN_READ));
    assertTrue(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(Group.EVERYONE), "CO", "a:fun-group", Permissions.CAN_READ));
  }

  @Test
  public void respectPrivateVisibilityPreference() throws Exception {
    assertTrue(adminAuthorizableManager.createGroup("secret-group", "secret-group", ImmutableMap.of(UserConstants.PROP_GROUP_MANAGERS, (Object) new String[]{"suzy"})));
    // create a user to be manager
    assertTrue(adminAuthorizableManager.createUser("suzy", "suzy", "secret", ImmutableMap.of("firstName", (Object) "Suzy", "lastName", "Queue")));
    adminAccessControlManager.setAcl("CO", "a:secret-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AU", "secret-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AD", "GR", new AclModification[] { new AclModification("suzy@g", Permissions.CAN_WRITE.getPermission(), AclModification.Operation.OP_REPLACE)});

    // set preference to private
    defaultPostProcessor.modified(ImmutableMap.of("visibility.preference", "private"));

    session = repository.loginAdministrative("suzy");
    Authorizable secretGroup = session.getAuthorizableManager().findAuthorizable("secret-group");
    when(mockChange.getType()).thenReturn(ModificationType.CREATE);
    // process now to establish a different group
    defaultPostProcessor.process(secretGroup, session, mockChange, parameters);

    // private means anonymous cannot read and neither can everyone
    assertFalse(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(User.ANON_USER), "CO", "a:secret-group", Permissions.CAN_READ));
    assertFalse(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(Group.EVERYONE), "CO", "a:secret-group", Permissions.CAN_READ));


  }

  @Test
  public void respectPublicVisibilityPreference() throws Exception {
    assertTrue(adminAuthorizableManager.createGroup("open-group", "open-group",
      ImmutableMap.of(UserConstants.PROP_GROUP_MANAGERS, (Object) new String[]{"suzy"}, UserConstants.PROP_GROUP_VIEWERS, new String[]{"zach"})));
    // create a user to be manager
    assertTrue(adminAuthorizableManager.createUser("suzy", "suzy", "secret", ImmutableMap.of("firstName", (Object) "Suzy", "lastName", "Queue")));
    adminAccessControlManager.setAcl("CO", "a:open-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AU", "open-group", new AclModification[] { new AclModification("suzy@g", ALL_ACCESS, AclModification.Operation.OP_REPLACE)});
    adminAccessControlManager.setAcl("AD", "GR", new AclModification[] { new AclModification("suzy@g", Permissions.CAN_WRITE.getPermission(), AclModification.Operation.OP_REPLACE)});

    // set preference to private
    defaultPostProcessor.modified(ImmutableMap.of("visibility.preference", "public"));

    session = repository.loginAdministrative("suzy");
    Authorizable openGroup = session.getAuthorizableManager().findAuthorizable("open-group");
    when(mockChange.getType()).thenReturn(ModificationType.CREATE);
    // process now to establish a different group
    defaultPostProcessor.process(openGroup, session, mockChange, parameters);

    // public means anonymous and everyone can both read
    assertTrue(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(User.ANON_USER), "CO", "a:open-group", Permissions.CAN_READ));
    assertTrue(adminAccessControlManager.can(adminAuthorizableManager.findAuthorizable(Group.EVERYONE), "CO", "a:open-group", Permissions.CAN_READ));


  }
}

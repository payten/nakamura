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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.resource.MoveCleaner.RESOURCE_TYPE;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocPageMoveCleaner.LTI_KEYS_NODE;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocPageMoveCleaner.MESSAGESTORE_PROP;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocPageMoveCleaner.MIMETYPE;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocPageMoveCleaner.SAKAI_DOC_MIMETYPE;

import java.util.List;

import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SakaiDocPageMoveCleanerTest {
  SakaiDocPageMoveCleaner cleaner;

  @Before
  public void setUp() {
    cleaner = new SakaiDocPageMoveCleaner();
  }

  @Test
  public void testNothingToClean() throws Exception {
    String fromPath = "from";
    String toPath = "to";

    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    adminContentManager.update(new Content(toPath, ImmutableMap.<String, Object> of(
        MIMETYPE, SAKAI_DOC_MIMETYPE)));

    List<Modification> mods = cleaner.clean(fromPath, toPath, adminContentManager);
    assertNotNull(mods);
    assertEquals(0, mods.size());
  }

  @Test
  public void testCleanAfterMove() throws Exception {
    String fromPath = "from";
    String toPath = "to";
    String pageId = "id123";
    String pagePath = toPath + "/" + pageId;

    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();
    AccessControlManager adminACM = adminSession.getAccessControlManager();

    adminContentManager.update(new Content(toPath, ImmutableMap.<String, Object> of(
        RESOURCE_TYPE, "sakai/pooled-content", MIMETYPE, SAKAI_DOC_MIMETYPE)));
    adminContentManager.update(new Content(pagePath, ImmutableMap
        .<String, Object> of("yay", "data")));
    adminContentManager.update(new Content(pagePath + "/rows", ImmutableMap
        .<String, Object> of("yay", "page")));

    // test with a discussion widget.
    adminContentManager.update(new Content(pagePath + "/id234/discussion",
        ImmutableMap.<String, Object> of(MESSAGESTORE_PROP, fromPath + "/weee")));

    List<Modification> mods = cleaner.clean(fromPath, pagePath,
        adminContentManager);
    assertNotNull(mods);
    assertEquals(1, mods.size());

    Content discussion = adminContentManager.get(pagePath + "/id234/discussion");
    assertEquals(pagePath + "/weee", discussion.getProperty(MESSAGESTORE_PROP));


    // test with a comments widget. since discussion widget was fixed above we should only
    // see 1 modification.
    adminContentManager.update(new Content(pagePath + "/id234/comments", ImmutableMap
        .<String, Object> of(MESSAGESTORE_PROP, fromPath + "/good_times")));

    mods = cleaner.clean(fromPath, pagePath, adminContentManager);
    assertNotNull(mods);
    assertEquals("Should've cleaned comments message store path.", 1, mods.size());
    assertEquals(ModificationType.MODIFY, mods.get(0).getType());

    Content comments = adminContentManager.get(pagePath + "/id234/comments");
    assertEquals(pagePath + "/good_times", comments.getProperty(MESSAGESTORE_PROP));


    // test with 2 new nodes. should be 2 modifications
    adminContentManager.update(new Content(pagePath + "/id235/comments", ImmutableMap
        .<String, Object> of(MESSAGESTORE_PROP, fromPath + "/more_fun")));
    adminContentManager.update(new Content(pagePath + "/id235/discussion", ImmutableMap
        .<String, Object> of(MESSAGESTORE_PROP, fromPath + "/going_crazy")));

    mods = cleaner.clean(fromPath, pagePath, adminContentManager);
    assertNotNull(mods);
    assertEquals("Should've cleaned comments & discussion message store path.", 2,
        mods.size());
    assertEquals(ModificationType.MODIFY, mods.get(0).getType());
    assertEquals(ModificationType.MODIFY, mods.get(1).getType());

    comments = adminContentManager.get(pagePath + "/id235/comments");
    discussion = adminContentManager.get(pagePath + "/id235/discussion");
    assertEquals(pagePath + "/more_fun", comments.getProperty(MESSAGESTORE_PROP));
    assertEquals(pagePath + "/going_crazy", discussion.getProperty(MESSAGESTORE_PROP));


    // test with basiclti node. this has a special ltiKeys node that is moved during
    // cleaning.
    Repository repo = Mockito.mock(Repository.class);
    when(repo.loginAdministrative()).thenReturn(adminSession);
    cleaner.repository = repo;
    String fromPagePath = fromPath + "/" + pageId;
    // construct the node that is possible to move
    adminContentManager.update(new Content(pagePath + "/id235/basiclti",
        ImmutableMap.<String, Object> of("random", "stuff")));
    // construct the node that gets left behind because it is secured
    adminContentManager.update(new Content(fromPagePath + "/id235/basiclti/"
        + LTI_KEYS_NODE, ImmutableMap.<String, Object> of("ltisecret", "reallysecret")));

    String fromLtiKeys = fromPagePath + "/id235/basiclti/" + LTI_KEYS_NODE;
    String toLtiKeys = pagePath + "/id235/basiclti/" + LTI_KEYS_NODE;
    // setup the ACLs that should move with the ltiKeys node
    adminACM.setAcl(
        Security.ZONE_CONTENT,
        fromLtiKeys,
        new AclModification[] {
            new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.ALL
                .getPermission(), Operation.OP_REPLACE)});

    User anonUser = new User(ImmutableMap.<String, Object> of(User.ID_FIELD,
        User.ANON_USER));
    Group everyone = new Group(ImmutableMap.<String, Object> of(User.ID_FIELD,
        Group.EVERYONE));
    // do a sanity check to make sure our assumptions are true before proceeding
    assertFalse("Anonymous shouldn't be able to read the 'from' secret node",
        adminACM.can(anonUser, Security.ZONE_CONTENT, fromLtiKeys, Permissions.CAN_READ));
    assertFalse("Anonymous shouldn't be able to write to the 'from' secret node",
        adminACM.can(anonUser, Security.ZONE_CONTENT, fromLtiKeys, Permissions.CAN_WRITE));
    assertFalse("Everyone shouldn't be able to read the 'from' secret node",
        adminACM.can(everyone, Security.ZONE_CONTENT, fromLtiKeys, Permissions.CAN_READ));
    assertFalse("Everyone shouldn't be able to write to the 'from' secret node",
        adminACM.can(everyone, Security.ZONE_CONTENT, fromLtiKeys, Permissions.CAN_WRITE));

    mods = cleaner.clean(fromPagePath, pagePath, adminContentManager);
    assertNotNull(mods);
    assertEquals("Should've moved the ltiKeys node.", 1, mods.size());
    assertEquals(ModificationType.MOVE, mods.get(0).getType());

    assertFalse("Anonymous shouldn't be able to read the 'to' secret node",
        adminACM.can(anonUser, Security.ZONE_CONTENT, toLtiKeys, Permissions.CAN_READ));
    assertFalse("Anonymous shouldn't be able to write to the 'to' secret node",
        adminACM.can(anonUser, Security.ZONE_CONTENT, toLtiKeys, Permissions.CAN_WRITE));
    assertFalse("Everyone shouldn't be able to read the 'to' secret node",
        adminACM.can(everyone, Security.ZONE_CONTENT, toLtiKeys, Permissions.CAN_READ));
    assertFalse("Everyone shouldn't be able to write to the 'to' secret node",
        adminACM.can(everyone, Security.ZONE_CONTENT, toLtiKeys, Permissions.CAN_WRITE));
  }
}

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
package org.sakaiproject.nakamura.util;

import static org.easymock.EasyMock.expect;

import junit.framework.Assert;

import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.content.Content;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

/**
 *
 */
public class LitePersonalUtilsTest {

  private Authorizable adminUser;
  private Authorizable myGroup;
  private String groupName = "mygroup";
  private String userName = "admin";
  private String groupPublicPath = "a:mygroup/public";
  private String userPublicPath = "a:admin/public";
  private String groupPrivatePath = "a:mygroup/private";
  private String userPrivatePath = "a:admin/private";

  private List<Object> mocks;


  @Before
  public void setUp() throws Exception {
    mocks = new ArrayList<Object>();

    adminUser = createAuthorizable(userName, false, true);
    myGroup = createAuthorizable(groupName, true, true);
  }

  @Test
  public void testPublicPath() throws Exception {
    // Group
    String result = LitePersonalUtils.getPublicPath(myGroup.getID());
    Assert.assertEquals(groupPublicPath, result);

    // User
    result = LitePersonalUtils.getPublicPath(adminUser.getID());
    Assert.assertEquals(userPublicPath, result);
  }

  @Test
  public void testProfilePath() throws Exception {
    String result = LitePersonalUtils.getProfilePath(adminUser.getID());
    Assert.assertEquals(userPublicPath + "/authprofile", result);
  }

  @Test
  public void testPrivatePath() throws Exception {
    // Group
    String result = LitePersonalUtils.getPrivatePath(myGroup.getID());
    Assert.assertEquals(groupPrivatePath, result);

    // User
    result = LitePersonalUtils.getPrivatePath(adminUser.getID());
    Assert.assertEquals(userPrivatePath, result);
  }

  @Test
  public void testGetPrefferedMailTransport() throws Exception {
    String pref = "internal";
    Map<String, Object> props = ImmutableMap.of(LitePersonalUtils.PROP_PREFERRED_MESSAGE_TRANSPORT,
        (Object) pref);
    Content node = new Content("user", props);

    String result = LitePersonalUtils.getPreferredMessageTransport(node);
    Assert.assertEquals(pref, result);
  }

  @Test
  public void testGetEmailAddresses() throws RepositoryException {
    String[] mails = { "foo@bar.com", "test@test.com" };
    Map<String, Object> props = ImmutableMap.of(LitePersonalUtils.PROP_EMAIL_ADDRESS,
        (Object) mails);
    Content node = new Content("user", props);
    
    String[] result = LitePersonalUtils.getEmailAddresses(node);
    for (int i = 0; i < mails.length; i++) {
      Assert.assertEquals(mails[i], result[i]);
    }
  }

  @Test
  public void testPrimaryEmailAddress() throws RepositoryException {
    String[] mails = { "foo@bar.com", "test@test.com" };
    Map<String, Object> props = ImmutableMap.of(LitePersonalUtils.PROP_EMAIL_ADDRESS,
        (Object) mails[0]);
    Content node = new Content("user", props);

    String result = LitePersonalUtils.getPrimaryEmailAddress(node);
    Assert.assertEquals(mails[0], result);
  }

  /**
   * @param id
   *          The id of the {@link User user} or {@link Group group}.
   * @param isGroup
   *          Whether or not this authorizable is a group.
   * @param doReplay
   *          Whether or not this mock should be replayed.
   * @return A mocked {@link Authorizable Authorizable}.
   * @throws RepositoryException
   */
  protected Authorizable createAuthorizable(String id, boolean isGroup, boolean doReplay)
      throws RepositoryException {
    Authorizable au = EasyMock.createMock(Authorizable.class);
    expect(au.getID()).andReturn(id).anyTimes();
    expect(au.isGroup()).andReturn(isGroup).anyTimes();
    ItemBasedPrincipal p = EasyMock.createMock(ItemBasedPrincipal.class);
    String hashedPath = "/"+id.substring(0,1)+"/"+id.substring(0,2)+"/"+id;
    expect(p.getPath()).andReturn("rep:" + hashedPath).anyTimes();
    expect(au.getPrincipal()).andReturn(p).anyTimes();
    expect(au.hasProperty("path")).andReturn(true).anyTimes();
    Value v = EasyMock.createNiceMock(Value.class);
    expect(v.getString()).andReturn(hashedPath).anyTimes();
    expect(au.getProperty("path")).andReturn(new Value[] { v }).anyTimes();
    EasyMock.replay(p);
    EasyMock.replay(v);
    if (doReplay) {
      EasyMock.replay(au);
    }
    return au;
  }
  protected <T> T createMock(Class<T> c) {
    T result = org.easymock.EasyMock.createMock(c);
    mocks.add(result);
    return result;
  }
  protected void replay() {
    org.easymock.EasyMock.replay(mocks.toArray());
  }

}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MessageRouteImplTest {

  @Test
  public void testNullRoute() {
    MessageRouteImpl mri = new MessageRouteImpl(null);
    assertNull(mri.getRcpt());
    assertNull(mri.getTransport());
  }

  @Test
  public void testEmptyRoute() {
    MessageRouteImpl mri = new MessageRouteImpl("");
    assertEquals("internal", mri.getTransport());
    assertEquals("", mri.getRcpt());
  }

  @Test
  public void testInternalRoute() {
    MessageRouteImpl mri = new MessageRouteImpl(":admin");
    assertEquals("internal", mri.getTransport());
    assertEquals("admin", mri.getRcpt());
  }

  @Test
  public void testExternalRoute() {
    MessageRouteImpl mri = new MessageRouteImpl("smtp:admin@localhost");
    assertEquals("smtp", mri.getTransport());
    assertEquals("admin@localhost", mri.getRcpt());
  }
}

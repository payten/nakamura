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

import java.util.Map;

import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.message.MessageConstants;

import com.google.common.collect.ImmutableMap;

public class LiteMessageRoutesImplTest {

  @Test
  public void testConstructWithNode() throws Exception {
    Map<String, Object> props = ImmutableMap.of(MessageConstants.PROP_SAKAI_TO,
        (Object) "smtp:foo@localhost,smtp:bar@localhost");
    Content c = new Content("", props);
    LiteMessageRoutesImpl mri = new LiteMessageRoutesImpl(c);
    assertEquals(2, mri.size());
  }
}

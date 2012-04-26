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
package org.sakaiproject.nakamura.version.impl.sparse;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class SparseGetVersionServletHandlerTest {

  @Test
  public void ensureResourceTypeIsNotNull() throws Exception {
    // this test was motivated by: https://jira.sakaiproject.org/browse/KERN-2603
    Content mockContent = mock(Content.class);
    ContentManager mockContentManager = mock(ContentManager.class);
    Resource mockResource = mock(Resource.class);
    String versionName = "abcd";
    ResourceWrapper testWrapper =
      SparseGetVersionServletHandler.getVersionResourceWrapper(mockResource, versionName, mockContent, mockContentManager);
    assertNull(mockContent.getProperty("sakai:resourceType"));
    assertNotNull(testWrapper.getResourceType());
  }
}

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
package org.sakaiproject.nakamura.files.search;

import static org.mockito.Mockito.when;

import static junit.framework.Assert.assertEquals;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import com.google.common.collect.Maps;

import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GroupReaderSearchPropertyProviderTest {
  @Mock
  SlingHttpServletRequest request;

  GroupReaderSearchPropertyProvider propProvider;

  @Before
  public void setUp() {
    propProvider = new GroupReaderSearchPropertyProvider();
  }

  @Test
  public void noGroupProducesNoProp() {
    Map<String, String> props = Maps.newHashMap();
    propProvider.loadUserProperties(request, props);

    assertTrue(props.isEmpty());
  }

  @Test
  public void groupProducesProp() {
    when(request.getParameter("group")).thenReturn("test");

    Map<String, String> props = Maps.newHashMap();
    propProvider.loadUserProperties(request, props);

    assertFalse(props.isEmpty());

    assertEquals(" AND readers:test", props.get("_groupReaderAnd"));
  }
}

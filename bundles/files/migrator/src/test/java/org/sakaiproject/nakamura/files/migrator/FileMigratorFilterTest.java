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

package org.sakaiproject.nakamura.files.migrator;

import static org.mockito.Mockito.when;

import junit.framework.Assert;
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

public class FileMigratorFilterTest extends Assert {

  private FileMigratorFilter fileMigratorFilter;

  @Before
  public void setup() throws ServletException {
    this.fileMigratorFilter = new FileMigratorFilter();
    this.fileMigratorFilter.init(Mockito.mock(FilterConfig.class));
  }

  @Test
  public void isRequestForPrivspaceOrPubspace() {
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");

    when(request.getPathInfo()).thenReturn("/~user1/private/privspace");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~user1/public/pubspace");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~somebody@somewhere.com/public/pubspace");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~somebody@somewhere.com/private/privspace.json");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~somebody@somewhere" +
        ".com/private/privspace/subpath.json");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~somebody@???/private/privspace");
    assertTrue(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~user1/private/abc");
    assertFalse(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~user1/public/abc");
    assertFalse(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

    when(request.getPathInfo()).thenReturn("/~user1/ /abc");
    assertFalse(fileMigratorFilter.isRequestForPrivspaceOrPubspace(request));

  }

}

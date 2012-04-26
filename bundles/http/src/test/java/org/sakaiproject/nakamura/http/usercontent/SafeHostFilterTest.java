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
package org.sakaiproject.nakamura.http.usercontent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class SafeHostFilterTest {

  private SafeHostFilter safeHostFilter;
  @Mock
  private ServerProtectionService serverPotectionService;
  @Mock
  private ComponentContext componentContext;
  @Mock
  private HttpServletRequest request;
  @Mock
  private HttpServletResponse response;
  @Mock
  private FilterChain chain;
  
  @Before
  public void setup() throws ServletException {
    safeHostFilter = new SafeHostFilter();
    safeHostFilter.serverProtectionService = serverPotectionService;
    Dictionary<String, Object> properties = new Hashtable<String, Object>();
    Mockito.when(componentContext.getProperties()).thenReturn(properties);
  }

  @Test
  public void testDoFilter() throws IOException, ServletException {
    Mockito.when(serverPotectionService.isMethodSafe(request, response)).thenReturn(true);
    safeHostFilter.doFilter(request, response, chain);
    Mockito.verify(chain, Mockito.times(1)).doFilter(request, response);
  }
  @Test
  public void testDoNoFilter() throws IOException, ServletException {
    Mockito.when(serverPotectionService.isMethodSafe(request, response)).thenReturn(false);
    Mockito.when(request.getRequestURL()).thenReturn(new StringBuffer());
    safeHostFilter.doFilter(request, response, chain);
    Mockito.verify(chain, Mockito.times(0)).doFilter(request, response);
  }
}

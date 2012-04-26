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

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingFilter;
import org.apache.felix.scr.annotations.sling.SlingFilterScope;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * <p>
 * Sling filter that protects the server against POSTs to non safe hosts. GETs and HEADs
 * are allowed to pass without restriction.
 * </p>
 * <p>
 * Notice that this is a <em>Sling</em> filter which is called after the Sling resource
 * resolution mechanism is consulted. Check the <a
 * href="http://sling.apache.org/site/filters.html">Sling Filter documentation</a> for
 * more information about how they function.
 * </p>
 * 
 * @see org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl
 */
@Properties(value = {
    @Property(name = "service.description", value = "Filter to ensure a safe request"),
    @Property(name = "service.vendor", value = "The Sakai Foundation")
})
@SlingFilter(order = 10, scope = SlingFilterScope.REQUEST)
public class UserContentFilter implements Filter {


  private static final Logger LOGGER = LoggerFactory.getLogger(UserContentFilter.class);

  @Reference
  protected ServerProtectionService serverProtectionService;

  public void init(FilterConfig filterConfig) throws ServletException {    
  }

  public void destroy() {
  }
  
  /**
   * {@inheritDoc}
   * 
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
   * @see org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl#isRequestSafe(SlingHttpServletRequest,
   *      SlingHttpServletResponse)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
  throws IOException, ServletException {
    SlingHttpServletRequest srequest = (SlingHttpServletRequest) request;
    SlingHttpServletResponse sresponse = (SlingHttpServletResponse) response;
    if ( serverProtectionService.isRequestSafe(srequest, sresponse) ) {
        if ( serverProtectionService.isSafeHost(srequest)) {
          sresponse.setHeader("X-Frame-Option", "DENY"); // deny iframe embedding of OAE pages to prevent clickjacking on the safe host, we might want to make this SAMEORIGIN
        }
        chain.doFilter(request, response);
    } else {
      LOGGER.debug("Method {} is not safe on {} {}?{}",new Object[]{srequest.getMethod(),srequest.getRequestURL(),srequest.getQueryString()});
    }
  }

}

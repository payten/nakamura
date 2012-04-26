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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Servlet filter that protects the server against POSTs to non safe hosts. GETs and HEADs
 * are allowed to pass without restriction.
 * </p>
 * <p>
 * Notice that this is a standard servlet filter which is means it is:
 * <ol>
 * <li>not an OSGi service (just a component)</li>
 * <li>uses the <a href=
 * "http://felix.apache.org/site/apache-felix-http-service.html#ApacheFelixHTTPService-UsingtheWhiteboard"
 * >whiteboard "pattern" property to register with HttpService</a></li>
 * </ol>
 * Servlet filters are called before the Sling resource resolution mechanism is consulted.
 * Since this filter will be called on every request, it should not rely on a resource so
 * that the cost of resolving the resource is not incurred unnecessarily.
 * </p>
 * 
 * @see org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl
 */
@Component
@Service
@Properties(value = {
    @Property(name = "service.description", value = "Filter to ensure a safe host"),
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.ranking", intValue = 8),
    @Property(name = "pattern", value = ".*")
})
public class SafeHostFilter implements Filter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SafeHostFilter.class);

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
   * @see org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl#isMethodSafe(HttpServletRequest,
   *      HttpServletResponse)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
  throws IOException, ServletException {
    HttpServletRequest hrequest = (HttpServletRequest) request;
    HttpServletResponse hresponse = (HttpServletResponse) response;
    if (serverProtectionService.isMethodSafe(hrequest, hresponse)) {
      chain.doFilter(request, response);      
    } else {
      LOGGER.debug("Method {} is not safe on {} {}?{}",new Object[]{hrequest.getMethod(),hrequest.getServerName(),hrequest.getRequestURL(),hrequest.getQueryString()});
    }
  }
}

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
package org.sakaiproject.nakamura.jetty;

import org.apache.felix.http.api.ExtHttpService;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * This class that operates as a managed service.
 *
 * <p>The annotations on this class are just to generate the xml files saved in src/main/resources/OSGI-INF.
 * This differs from normal behavior because this bundle doesn't use the `bundle` packaging,
 * so that it can unwrap some other bundles.
 */
@Component(immediate = true, metatype = true)
@Service
@Properties({
  @Property(name="service.description", value="Nakamura GZip Filter"),
  @Property(name="service.vendor", value="The Sakai Foundation"),
  @Property(name="bufferSize", intValue=8192),
  @Property(name="minGzipSize", intValue=8192),
  // regex string:  (?:Mozilla[^\(]*\(compatible;\s*+([^;]*);.*)|(?:.*?([^\s]+/[^\s]+).*)
  // java string: (?:Mozilla[^\\(]*\\(compatible;\\s*+([^;]*);.*)|(?:.*?([^\\s]+/[^\\s]+).*)
  @Property(name="userAgent", value="SEE SOURCE FOR CORRECT VALUE"),
  @Property(name="mimeTypes", value="text/html,text/plain,text/css,text/javascript,text/xml,application/xml,application/xhtml+xml,application/rss+xml,application/javascript,application/x-javascript,application/json"), 
  @Property(name="excludedAgents", value=""),
  @Property(name="enabled", boolValue=false)
  // don't include a "pattern" property because we want to control whether the service is registered during activation
})
@Reference(name="extHttpService",
    referenceInterface=org.apache.felix.http.api.ExtHttpService.class,
    cardinality=ReferenceCardinality.MANDATORY_UNARY,
    policy=ReferencePolicy.STATIC)
public class OSGiGZipFilter extends GzipFilter {
  protected ExtHttpService extHttpService;

  private boolean enabled = false;

  @SuppressWarnings("rawtypes")
  @Activate
  public void activate(Map<String, Object> properties) throws ServletException {
    enabled = PropertiesUtil.toBoolean(properties.get("enabled"), false);
    if (enabled) {
      Hashtable<String, Object> props = new Hashtable<String, Object>();
      props.putAll(properties);
      extHttpService.registerFilter(this, ".*", props, 100, null);
    }
  }

  @Override
  public void doFilter(ServletRequest arg0, ServletResponse arg1, FilterChain arg2)
      throws IOException, ServletException {
    super.doFilter(arg0, arg1, arg2);
  }

  @Deactivate
  public void deactivate(Map<String, Object> properties) {
    if (enabled) {
      extHttpService.unregisterFilter(this);
    }
  }

  protected void bind(ExtHttpService extHttpService) {
    this.extHttpService = extHttpService;
  }

  protected void unbind(ExtHttpService extHttpService) {
    this.extHttpService = null;
  }

}

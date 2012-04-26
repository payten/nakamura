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
package org.sakaiproject.nakamura.templates.velocity;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.request.RequestParameter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.templates.TemplateNodeSource;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.jcr.Node;
import javax.jcr.Repository;

@Service
@Component(immediate = true)
public class VelocityTemplateService implements TemplateService, TemplateNodeSource {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(VelocityTemplateService.class);

  private VelocityEngine velocityEngine;

  @Reference
  protected Repository repository;

  public String evaluateTemplate(Map<String, ? extends Object> parameters, String template) {
    return evaluateTemplate(parameters, new StringReader(template));
  }

  public String evaluateTemplate(Map<String, ? extends Object> parameters, Reader templateReader) {
    Map<String, String> sanitizedParameters = sanitize(parameters);
    VelocityContext context = new VelocityContext(sanitizedParameters);
    // combine template with parameter map
    StringWriter templateWriter = new StringWriter();
    try {
      velocityEngine.evaluate(context, templateWriter, "templateprocessing",
          templateReader);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return templateWriter.toString();
  }

  private Map<String,String> sanitize(Map<String, ? extends Object> parameters) {
    Map<String,String> rv = new HashMap<String,String>();
    for (Entry<String, ? extends Object> param : parameters.entrySet()) {
      Object value = param.getValue();
      String key = param.getKey();
      if (value instanceof RequestParameter) {
        rv.put(key, String.valueOf((RequestParameter) value));
      } else if (value instanceof String[]) {
        String[] values = (String[])value;
        rv.put(key, values[0]);
      } else {
        rv.put(key, String.valueOf(value));
      }
    }
    return rv;
  }

  public Collection<String> missingTerms(String template) {
    if (template == null || StringUtils.isBlank(template)) {
      return Collections.emptyList();
    }

    Collection<String> missingTerms = new ArrayList<String>();
    int startPosition = template.indexOf("${");
    while (startPosition > -1) {
      int endPosition = template.indexOf("}", startPosition);
      if (endPosition > -1) {
        String key = template.substring(startPosition + 2, endPosition);
        missingTerms.add(key);
        // look for the next velocity replacement variable
        startPosition = template.indexOf("${", endPosition);
      } else {
        break;
      }
    }
    return missingTerms;
  }

  public Collection<String> missingTerms(Map<String, ? extends Object> parameters,
      String template) {
    if (template == null || StringUtils.isBlank(template)) {
      return Collections.emptyList();
    }

    Collection<String> missingTerms = new ArrayList<String>();
    int startPosition = template.indexOf("${");
    while (startPosition > -1) {
      int endPosition = template.indexOf("}", startPosition);
      if (endPosition > -1) {
        String key = template.substring(startPosition + 2, endPosition);
        Object value = parameters.get(key);
        if (value == null) {
          missingTerms.add(key);
        }
        // look for the next velocity replacement variable
        startPosition = template.indexOf("${", endPosition);
      } else {
        break;
      }
    }
    return missingTerms;
  }

  protected void activate(ComponentContext ctx) throws Exception {
    velocityEngine = new VelocityEngine();
    velocityEngine.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM, new VelocityLogger(
        this.getClass()));

    velocityEngine.setProperty(VelocityEngine.RESOURCE_LOADER, "jcr");
    velocityEngine.setProperty("jcr.resource.loader.class",
        JcrResourceLoader.class.getName());
    ExtendedProperties configuration = new ExtendedProperties();
    configuration.addProperty("jcr.resource.loader.resourceSource", this);
    velocityEngine.setExtendedProperties(configuration);
    velocityEngine.init();
  }

  public Node getNode() {
    // Velocity calls this, but it doesn't seem to make a difference in practice
    return null;
  }
}

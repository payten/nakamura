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

package org.sakaiproject.nakamura.upgrade;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;

import java.util.Map;

@Service
@Component
public class WorldTemplateIDFixer implements PropertyMigrator {

  private static final String TEMPLATE_PROP = "sakai:templateid";
  @Reference
  private Repository repository;

  @Override
  public boolean migrate(String rowID, Map<String, Object> properties) {
    boolean handled = false;
    Object templateID = properties.get(TEMPLATE_PROP);
    Object type = properties.get("type");
    if (templateID != null && "g".equals(type)) {
      if ("simplegroup".equals(templateID)) {
        properties.put(TEMPLATE_PROP, "simple-group");
        handled = true;
      }
      if ("basiccourse".equals(templateID)) {
        properties.put(TEMPLATE_PROP, "basic-course");
        handled = true;
      }
      if ("mathcourse".equals(templateID)) {
        properties.put(TEMPLATE_PROP, "math-course");
        handled = true;
      }
      if ("researchproject".equals(templateID)) {
        properties.put(TEMPLATE_PROP, "research-project");
        handled = true;
      }
      if ("researchsupport".equals(templateID)) {
        properties.put(TEMPLATE_PROP, "research-support");
        handled = true;
      }
    }
    return handled;
  }

  @Override
  public String[] getDependencies() {
    return new String[]{};
  }

  @Override
  public String getName() {
    return WorldTemplateIDFixer.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

}

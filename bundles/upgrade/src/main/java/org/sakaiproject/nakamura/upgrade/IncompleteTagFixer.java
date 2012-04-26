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
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/*
  KERN-2621: Fix directory tags that are missing sakai:tag-name or sling:resourceType
  Integration test for this migrator is kern-2621.rb
 */
@Service
@Component
public class IncompleteTagFixer implements PropertyMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncompleteTagFixer.class);

  @Override
  public boolean migrate(String rowID, Map<String, Object> properties) {

    boolean handled = false;
    Object objPath = properties.get("_path");
    if (objPath != null) {
      String path = (String) objPath;
      if (path.startsWith("/tags/directory/") && !("/tags/directory/".equals(path))) {
        Object resourceType = properties.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
        Object objTagName = properties.get(FilesConstants.SAKAI_TAG_NAME);
        if (resourceType == null || objTagName == null) {
          String tagName = path.substring("/tags/".length());
          properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
              FilesConstants.RT_SAKAI_TAG);
          properties.put(FilesConstants.SAKAI_TAG_NAME, tagName);
          LOGGER.info("We have a tag that needs to be fixed at path " + path);
          handled = true;
        }
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
    return IncompleteTagFixer.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

}

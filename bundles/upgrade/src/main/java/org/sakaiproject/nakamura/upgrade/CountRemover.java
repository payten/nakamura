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
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.RemoveProperty;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Remove count properties stored on authorizables; they'll regenerate the first time the
 * Basic User info service is called or the first time the count refresh job finds them.
 */

@Service
@Component
public class CountRemover implements PropertyMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(CountRemover.class);

  public boolean migrate(String rowID, Map<String, Object> properties) {
    Object val = properties.get("type");
    if (val != null) {
      if ("u".equals(val) || "g".equals(val)) {
        for (String propName : UserConstants.AUTHZ_COUNTS_PROPS) {
          removeProp(properties, propName);
        }
        if ("u".equals(val)) {
          for (String propName : UserConstants.USER_COUNTS_PROPS) {
            removeProp(properties, propName);
          }
        } else if ("g".equals(val)) {
          for (String propName : UserConstants.GROUP_COUNTS_PROPS) {
            removeProp(properties, propName);
          }
        }
        LOGGER.debug("Removed count props from authorizable at row " + rowID);
        return true;
      }
    }
    return false;
  }

  private void removeProp(Map<String, Object> properties, String propName) {
    properties.put(propName, new RemoveProperty());
  }

  public String[] getDependencies() {
    return new String[0];
  }

  public String getName() {
    return CountRemover.class.getName();
  }

  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }
}

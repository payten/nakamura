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

import java.util.Map;

/**
 * Rename property sakai:pseudogroupparent to sakai:parent-group-id
 */

@Service
@Component
public class PseudoGroupParentRenamer implements PropertyMigrator {

  private static final String OLD_PROP_NAME = "sakai:pseudogroupparent";

  private static final String NEW_PROP_NAME = "sakai:parent-group-id";

  public boolean migrate(String rowID, Map<String, Object> properties) {
    Object val = properties.get(OLD_PROP_NAME);
    if (val != null) {
      properties.put(NEW_PROP_NAME, val);
      properties.put(OLD_PROP_NAME, new RemoveProperty());
      return true;
    }
    return false;
  }

  public String[] getDependencies() {
    return new String[0];
  }

  public String getName() {
    return PseudoGroupParentRenamer.class.getName();
  }

  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }
}

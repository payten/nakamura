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
package org.sakaiproject.nakamura.api.templates;

import java.io.Reader;
import java.util.Collection;
import java.util.Map;

/**
 * Service to provide templating functionality for replacing variable markers in Strings.
 */
public interface TemplateService {

  /**
   * The VTL property name reserved for the ID generator map.
   */
  public String ID_GENERATOR = "_ids";

  String evaluateTemplate(Map<String, ? extends Object> parameters, String template);

  String evaluateTemplate(Map<String, ? extends Object> parameters, Reader templateReader);

  /**
   * Checks for unresolved variable markers in a processed template. Looks for ${param}
   * but does not look for $param.
   *
   * @param template Template to check
   * @return Collection of keys that were not resolved.
   */
  Collection<String> missingTerms(String template);

  /**
   * Checks for unresolved variable markers in a template. Looks for ${param} but does not
   * look for $param. Checks parameters to see if it can provide a value for any found
   * parameter keys.
   *
   * @param parameters Parameters to verify with
   * @param template Template to check
   * @return Collection of keys that were not resolvable.
   */
  Collection<String> missingTerms(Map<String, ? extends Object> parameters,
      String template);
}

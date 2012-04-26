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

package org.sakaiproject.nakamura.files.search;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
@Service
@Properties({
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "TagRefined")})
public class TagRefinedSearchPropertyProvider implements SolrSearchPropertyProvider {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(TagRefinedSearchPropertyProvider.class);

  public void loadUserProperties(SlingHttpServletRequest request,
                                 Map<String, String> propertiesMap) {
    String tagClause = "";
    String tagParam = request.getParameter("tags");
    if (!StringUtils.isBlank(tagParam) && !"*".equals(tagParam)) {
      tagClause = "AND tag:(" + tagParam + ")";
    }

    propertiesMap.put("_tags", tagClause);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("_tags = " + tagClause);
    }
  }

}

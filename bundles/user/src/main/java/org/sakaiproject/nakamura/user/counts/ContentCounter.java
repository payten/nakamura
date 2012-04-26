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
package org.sakaiproject.nakamura.user.counts;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.user.AuthorizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class ContentCounter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContentCounter.class);
  private static final String QUERY_TMPL = "(resourceType:sakai/pooled-content OR category:collection) AND ((manager:(%1$s) OR viewer:(%1$s))";
  private static final String USER_TMPL = " OR (showalways:true AND (manager:(%1$s) OR viewer:(%1$s)))";

  public int countExact(Authorizable au, AuthorizableManager authorizableManager,
      SolrServerService solrSearchService) throws StorageClientException,
      AccessDeniedException {
    if (au != null && !CountProvider.IGNORE_AUTHIDS.contains(au.getId())) {

      // find docs where the authz is a direct viewer or manager
      String userID = ClientUtils.escapeQueryChars(au.getId());
      String qs = String.format(QUERY_TMPL, userID);

      // for users, include indirectly managed or viewed documents whose showalways field is true
      if (!au.isGroup()) {
        // pooled-content-manager, pooled-content-viewer
        List<String> principals = Lists.newArrayList(userID);
        if (au.getPrincipals() != null) {
          for (String principal : au.getPrincipals()) {
            principals.add(ClientUtils.escapeQueryChars(principal));
          }
        }
        principals.remove(Group.EVERYONE);
        String readers = StringUtils.join(principals, " OR ");
        qs += String.format(USER_TMPL, readers);
      }

      qs += ")";
      int count = getCount(qs, solrSearchService);

      // for top level content collections, look up direct memberships
      // for each direct membership that is to a collection, add 1 to the count.
      if (AuthorizableUtil.isCollection(au, true)) {
        for (String principal : au.getPrincipals()) {
          if (!Group.EVERYONE.equals(principal)) {
            Authorizable memberAuth = authorizableManager.findAuthorizable(principal);
            if (AuthorizableUtil.isCollection(memberAuth, false)) {
              count += 1;
            }
          }
        }
      }

      return count;
    }
    return 0;
  }

  /**
   * @param queryString
   * @param solrSearchService
   * @return the count of results, we assume if they are returned the user can read them
   *         and we do not iterate through the entire set to check.
   */
  private int getCount(String queryString, SolrServerService solrSearchService) {
    SolrServer solrServer = solrSearchService.getServer();
    SolrQuery solrQuery = new SolrQuery(queryString).setRows(0);

    try {
      QueryResponse response = solrServer.query(solrQuery);
      return (int) response.getResults().getNumFound();
    } catch (SolrServerException e) {
      LOGGER.warn(e.getMessage(), e);
    }
    return 0;
  }
}

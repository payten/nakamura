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

package org.sakaiproject.nakamura.message.search;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.SearchResponseDecorator;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
@Properties(value = {
    @Property(name = Constants.SERVICE_VENDOR, value = "The Sakai Foundation"),
    @Property(name = Constants.SERVICE_DESCRIPTION, value = "Processor for message search results."),
    @Property(name = SolrSearchConstants.REG_SEARCH_DECORATOR_NAMES, value = "Message")
})
public class MessageSearchResponseDecorator implements SearchResponseDecorator {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageSearchResponseDecorator.class);

  @Reference
  protected LiteMessagingService messagingService;

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  public void decorateSearchResponse(SlingHttpServletRequest request, JSONWriter writer)
          throws JSONException {
    writer.key("unread");

    long count = 0;
    // We don't do queries for anonymous users. (Possible ddos hole).
    String userID = request.getRemoteUser();
    if (UserConstants.ANON_USERID.equals(userID)) {
      writer.value(count);
      return;
    }

    try {
      final Session session = StorageClientUtils.adaptToSession(request
              .getResourceResolver().adaptTo(javax.jcr.Session.class));
      String store = messagingService.getFullPathToStore(userID, session);
      store = ISO9075.encodePath(store);
      store = store.substring(0, store.length() - 1);
      String queryString = "path:" + ClientUtils.escapeQueryChars(store) +
              " AND resourceType:sakai/message AND type:internal AND messagebox:inbox AND read:false";
      Query query = new Query(queryString);
      SolrSearchResultSet resultSet = searchServiceFactory.getSearchResultSet(
              request, query, false);
      count = resultSet.getSize();
    } catch (SolrSearchException e) {
      LOGGER.error(e.getMessage());
    } finally {
      writer.value(count);
    }
  }

}

package org.sakaiproject.nakamura.user;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.user.UserFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@Component(immediate = true, metatype = true, label = "SolrUserFinder", description = "Find users using the Solr index")
@Service
public class SolrUserFinderImpl implements UserFinder {

  @Reference
  protected SolrServerService solrSearchService;

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrUserFinderImpl.class);

  /**
   * do a case insensitive solr search for user's name which is indexed as a
   * case-insensitive solr text field in AuthorizableIndexingHandler see KERN-2211
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.user.UserFinder#findUsersByName(java.lang.String)
   */
  public Set<String> findUsersByName(String name) throws Exception {
    Set<String> userIds = new HashSet<String>();
    SolrServer solrServer = solrSearchService.getServer();
    String queryString = "resourceType:authorizable AND type:u AND name:" + name;
    SolrQuery solrQuery = new SolrQuery(queryString);
    QueryResponse queryResponse = solrServer.query(solrQuery);
    SolrDocumentList results = queryResponse.getResults();
    for (SolrDocument solrDocument : results) {
      if (solrDocument.containsKey("id")) {
        userIds.add((String) solrDocument.getFieldValue("id"));
      }
    }
    LOGGER.debug("found these users by name: " + userIds);
    return userIds;
  }

  /**
   * using a case insensitive solr search, determine whether one or more users of this
   * name exist {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.user.UserFinder#userExists(java.lang.String)
   */
  public boolean userExists(String name) throws Exception {
    boolean userExists = false;
    Set<String> userIds = findUsersByName(name);
    if (!userIds.isEmpty()) {
      userExists = true;
    }
    LOGGER.debug("user with name " + name + " exists: " + userExists);
    return userExists;
  }

}

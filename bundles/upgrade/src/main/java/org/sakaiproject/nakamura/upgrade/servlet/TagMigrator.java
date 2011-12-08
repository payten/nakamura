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

package org.sakaiproject.nakamura.upgrade.servlet;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

@Component
@Service(value = TagMigrator.class)
public class TagMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(TagMigrator.class);

  private static final String SYSTEM_LOG_PATH = "system/v1.1.1-tagmigratorrunlog";

  private static final int ROWS_PER_SEARCH = 5000;

  @Reference
  private SlingRepository slingRepository;

  @Reference
  private Repository sparseRepository;

  @Reference
  private SolrServerService solrSearchService;

  public void migrate(final SlingHttpServletResponse response, boolean dryRun, boolean reindexAll)
          throws RepositoryException, StorageClientException, AccessDeniedException, SolrServerException {

    org.sakaiproject.nakamura.api.lite.Session sparseSession = null;
    Session jcrSession = null;

    try {
      sparseSession = this.sparseRepository.loginAdministrative();
      jcrSession = this.slingRepository.loginAdministrative("default");

      if (needsMigration(sparseSession) || reindexAll) {
        SparseUpgradeServlet.writeToResponse("Migrating tags from JCR to Sparse...", response);
        Set<String> allTags = getUniqueTags();
        ensureTagsExistInJCR(allTags, jcrSession, dryRun);
        saveTagsInSparse(jcrSession, sparseSession, dryRun);
      } else {
        SparseUpgradeServlet.writeToResponse("Tag Migrator already ran on this system, skipping migration", response);
      }

    } finally {
      if (sparseSession != null) {
        sparseSession.logout();
      }
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
  }

  private boolean needsMigration(org.sakaiproject.nakamura.api.lite.Session session)
          throws StorageClientException, AccessDeniedException {
    ContentManager cm = session.getContentManager();
    return !cm.exists(SYSTEM_LOG_PATH);
  }

  private Set<String> getUniqueTags() throws SolrServerException {
    Set<String> allTags = new HashSet<String>();
    int start = 0;
    int processedDocs = 0;

    while (true) {
      SolrDocumentList taggedDocuments = getMoreDocuments(start);
      for (SolrDocument taggedDocument : taggedDocuments) {
        processedDocs++;
        Collection<Object> tags = taggedDocument.getFieldValues("tag");
        if (tags != null) {
          for (Object o : tags) {
            if (o instanceof String) {
              String tag = (String) o;
              allTags.add(tag);
            }
          }
        }
      }
      if (taggedDocuments.getNumFound() > (taggedDocuments.getStart() + ROWS_PER_SEARCH)) {
        start = start + ROWS_PER_SEARCH;
      } else {
        break;
      }
    }

    LOGGER.info("Processed " + processedDocs + " total documents for tags");
    LOGGER.info("We have " + allTags.size() + " unique tags represented in Solr indexes: " + allTags);
    return allTags;
  }

  private SolrDocumentList getMoreDocuments(int start) throws SolrServerException {
    SolrQuery query = new SolrQuery("tag:*");
    query.setFields("tag"); // we only need the tag field
    query.setRows(ROWS_PER_SEARCH);
    query.setStart(start);
    // go direct to solr server so we can get all documents more easily
    QueryResponse solrResponse = this.solrSearchService.getServer().query(query);
    LOGGER.info("Got " + solrResponse.getResults().getNumFound() + " tagged documents from solr index; " +
            "this batch starts at " + solrResponse.getResults().getStart());
    return solrResponse.getResults();
  }

  private void ensureTagsExistInJCR(Set<String> allTags, Session session, boolean dryRun) throws RepositoryException {
    Node jcrTags;
    try {
      jcrTags = session.getNode("/tags");
    } catch (PathNotFoundException ignored) {
      jcrTags = session.getRootNode().addNode("tags");
    }
    for (String tag : allTags) {
      if (!jcrTags.hasNode(tag)) {
        LOGGER.info("JCR lacks the tag " + tag + ", creating it...");
        if (!dryRun) {
          jcrTags.addNode(tag);
        }
      }
    }
    if (!dryRun) {
      session.save();
    }

  }

  private void saveTagsInSparse(Session jcrSession, org.sakaiproject.nakamura.api.lite.Session sparseSession, boolean dryRun)
          throws StorageClientException, AccessDeniedException, RepositoryException {

    ContentManager cm = sparseSession.getContentManager();

    // loop over tags in jcr and save them to sparse
    Node jcrTags = jcrSession.getNode("/tags");
    if (jcrTags != null) {
      NodeIterator iterator = jcrTags.getNodes();
      while (iterator.hasNext()) {
        Node node = iterator.nextNode();

        // make sure we only recreate actual sakai:tag nodes
        if (node.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
          if (node.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).
                  getString().equals(FilesConstants.RT_SAKAI_TAG)) {
            String tag = node.getName();
            String tagPath = "/tags/" + tag;
            if (cm.exists(tagPath)) {
              LOGGER.info("Tag " + tag + " already exists in sparse, skipping");
            } else {
              LOGGER.info("Tag " + tag + " does not yet exist in sparse, creating");
              Content content = new Content(tagPath,
                      ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                              (Object) FilesConstants.RT_SAKAI_TAG));
              content.setProperty(FilesConstants.SAKAI_TAG_NAME, tag);
              if (!dryRun) {
                cm.update(content);
              }
            }
          }
        }
      }
    }

    // log when the migrator ran if this is for real
    if (!dryRun) {
      Content systemLog = new Content(SYSTEM_LOG_PATH,
              ImmutableMap.<String, Object>of("migrationTime", System.currentTimeMillis()));
      cm.update(systemLog);
    }

  }

}

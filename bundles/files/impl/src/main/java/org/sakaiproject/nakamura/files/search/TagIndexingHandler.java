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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Handler for indexing tag nodes. This aids in searching for tags or categories that
 * aren't assigned to any nodes and to flatten the broad lookup for tags that exist in the
 * system.
 */
@Component(immediate = true)
public class TagIndexingHandler implements IndexingHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(TagIndexingHandler.class);

  // index sakai:tag-name as 'tagname' so that we don't interfere with searching of data
  // that has been tagged but is not a tag. this leaves 'tag' empty for sakai/tag
  // resources in the index
  private static final Map<String, String> PROPERTIES = ImmutableMap.of(
      FilesConstants.SAKAI_TAG_NAME, "tagname");
  private static final Set<String> CONTENT_TYPES = Sets.newHashSet("sakai/tag");

  @Reference(target="(type=sparse)")
  protected ResourceIndexingService resourceIndexingService;

  // ---------- SCR integration-------------------------------------------------
  @Activate
  protected void activate() {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.addHandler(type, this);
    }
  }

  @Deactivate
  protected void deactivate() {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.removeHandler(type, this);
    }
  }

  // ---------- IndexingHandler interface --------------------------------------
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession, org.osgi.service.event.Event)
   */
  @Override
  public Collection<SolrInputDocument> getDocuments(RepositorySession repoSession, Event event) {
    LOGGER.debug("getDocuments for {}", event);
    String path = (String) event.getProperty(IndexingHandler.FIELD_PATH);

    List<SolrInputDocument> documents = Lists.newArrayList();
    if (!StringUtils.isBlank(path)) {
      Session session = repoSession.adaptTo(Session.class);
      try {
        ContentManager cm = session.getContentManager();
        Content content = cm.get(path);
        SolrInputDocument doc = new SolrInputDocument();
        for (Entry<String, String> prop: PROPERTIES.entrySet()) {
          String key = prop.getKey();
          Object value = content.getProperty(key);
          if (value != null) {
            doc.addField(PROPERTIES.get(key), value);
          }
        }
        doc.setField(_DOC_SOURCE_OBJECT, content);
        documents.add(doc);
      } catch (StorageClientException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    return documents;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession, org.osgi.service.event.Event)
   */
  @Override
  public Collection<String> getDeleteQueries(RepositorySession repoSession, Event event) {
    List<String> retval = Collections.emptyList();
    LOGGER.debug("getDeleteQueries for {}", event);
    String path = (String) event.getProperty(IndexingHandler.FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (CONTENT_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(path));
    }
    return retval;
  }
}

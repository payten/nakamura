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
package org.sakaiproject.nakamura.pages.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexing handler for widget data stored under a group. See {@link https
 * ://confluence.sakaiproject.org/display/KERNDOC/KERN-1675+Searching+Widget+Data} for
 * more details.
 */
@Component(immediate = true)
@Service
public class WidgetDataIndexingHandler implements IndexingHandler {

  public static final String INDEXED_FIELDS = "sakai:indexed-fields";

  private static final Logger logger = LoggerFactory
      .getLogger(WidgetDataIndexingHandler.class);

  private static final Set<String> CONTENT_TYPES = Sets.newHashSet("sakai/widget-data");

  @Reference(target = "(type=sparse)")
  private ResourceIndexingService resourceIndexingService;

  @Activate
  public void activate(Map<String, Object> properties) throws Exception {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.addHandler(type, this);
    }
  }

  @Deactivate
  public void deactivate(Map<String, Object> properties) {
    for (String type : CONTENT_TYPES) {
      resourceIndexingService.removeHandler(type, this);
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    String path = (String) event.getProperty(FIELD_PATH);

    Collection<SolrInputDocument> docs = Lists.newArrayList();
    if (!StringUtils.isBlank(path)) {
      try {
        Session session = repositorySession.adaptTo(Session.class);
        ContentManager cm = session.getContentManager();
        Content content = cm.get(path);

        if (content == null || !CONTENT_TYPES.contains(content.getProperty("sling:resourceType"))) {
          return docs;
        }

        // TODO get the path to the document where the widget lives
        String[] pathParts = StringUtils.split(path, "/", 2);
        String docPath = pathParts[0];

        Object fields = content.getProperty(INDEXED_FIELDS);
        Set<String> uniqFields = null;
        if (fields instanceof String) {
          uniqFields = ImmutableSet.copyOf(StringUtils.split(String.valueOf(fields), ","));
        } else if (fields instanceof String[]) {
          uniqFields = ImmutableSet.copyOf((String[]) fields);
        }

        // concatenate the fields requested to be indexed.
        StringBuilder sb = new StringBuilder();
        if (uniqFields != null) {
          for (String uniqField : uniqFields) {
            Object propVal = content.getProperty(uniqField);
            if (propVal != null) {
              sb.append(propVal).append(' ');
            }
          }
        }


        SolrInputDocument doc = new SolrInputDocument();
        // set the path here so that it's the first path found when rendering to
        // the client. we want this one first, so we don't have to create a
        // special result processor
        doc.setField(FIELD_PATH, docPath);

        // grab the parent document's filename to use for general sorting
        Content parentContent = cm.get(docPath);
        Map<String, Object> parentProperties = parentContent.getProperties();
        Object parentFilenameObj = parentProperties
            .get(FilesConstants.POOLED_CONTENT_FILENAME);
        if (parentFilenameObj != null) {
          doc.addField("general_sort", String.valueOf(parentFilenameObj));
        }
        
        // set the return to a single value field so we can group it
        doc.setField("returnpath", docPath);
        doc.setField("widgetdata", sb.toString());
        doc.addField(_DOC_SOURCE_OBJECT, content);

        docs.add(doc);
      } catch (StorageClientException e) {
        logger.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        logger.warn(e.getMessage(), e);
      }
    }
    return docs;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<String> getDeleteQueries(RepositorySession repositorySession,
      Event event) {
    List<String> retval = Collections.emptyList();
    logger.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty(FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (CONTENT_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(path));
    }
    return retval;
  }
}

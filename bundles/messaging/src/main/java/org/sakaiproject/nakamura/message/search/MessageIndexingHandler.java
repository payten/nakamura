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
package org.sakaiproject.nakamura.message.search;

import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_BODY;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_CREATED;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SUBJECT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.QoSIndexHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes messages for searching as autonomous items or as part of the user,group
 * searching.
 */
@Component(immediate = true)
public class MessageIndexingHandler implements IndexingHandler, QoSIndexHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(MessageIndexingHandler.class);

  /* 
   * TODO: This conversion is currently a work-around due to data-type inconsistency found in the
   * sakai:message property. When a message is first created the property is serialized as a
   * Calendar, but some time later it ends up being changed to a string. To handle this inconsistency
   * we need a secondary check (if it is not a Calendar object) to convert it to date from String.
   * This formatter is the default Calendar.toString() format that ends up being persisted.
   * 
   * e.g., Tue Apr 10 2012 13:39:50 GMT-0400
   * 
   * This is only to side-step data-migration for the 1.2.0 release.
   */
  private static final SimpleDateFormat SAKAI_CREATED_DATE_CONVERSION = new SimpleDateFormat(
      "EEE MMM dd yyyy HH:mm:ss 'GMT'Z");
  
  private static final Map<String, String> WHITELISTED_PROPS;
  static {
    Builder<String,String> propBuilder = ImmutableMap.builder();
    propBuilder.put("sakai:messagestore", "messagestore");
    propBuilder.put("sakai:messagebox", "messagebox");
    propBuilder.put("sakai:type", "type");
    propBuilder.put("sakai:category", "category");
    propBuilder.put("sakai:from", "from");
    propBuilder.put("sakai:to", "to");
    propBuilder.put("sakai:read", "read");
    propBuilder.put("sakai:marker", "marker");
    propBuilder.put("sakai:sendstate", "sendstate");
    propBuilder.put("sakai:initialpost", "initialpost");
    propBuilder.put(PROP_SAKAI_SUBJECT, "title");
    propBuilder.put(PROP_SAKAI_BODY, "content");
    WHITELISTED_PROPS = propBuilder.build();
  }

  private static final String AUTH_SUFFIX = "-auth";

  private static final Set<String> CONTENT_TYPES = Sets
      .newHashSet(MessageConstants.SAKAI_MESSAGE_RT);

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
   * @see org.sakaiproject.nakamura.api.solr.QoSIndexHandler#getTtl(org.osgi.service.event.Event)
   */
  public int getTtl(Event event) {
    // have to be > 0 based on the logic in ContentEventListener.
    // see org.sakaiproject.nakamura.solr.Utils.defaultMax(int)
    return 50;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    String path = (String) event.getProperty(IndexingHandler.FIELD_PATH);

    List<SolrInputDocument> documents = Lists.newArrayList();
    if (!StringUtils.isBlank(path) && !isTemporaryContent(path)) {
      try {
        Session session = repositorySession.adaptTo(Session.class);
        ContentManager cm = session.getContentManager();
        Content content = cm.get(path);

        if (content != null) {
          if (!CONTENT_TYPES.contains(content.getProperty("sling:resourceType"))) {
            return documents;
          }

          // index as autonomous message
          SolrInputDocument doc = new SolrInputDocument();
          for (String prop : WHITELISTED_PROPS.keySet()) {
            Object value = content.getProperty(prop);
            if (value != null) {
              doc.addField(WHITELISTED_PROPS.get(prop), value);
            }
          }

          // Use sakai:created field as the indexed created field. The internal _created content
          // field is pretty much useless.
          Object createdObj = content.getProperty(PROP_SAKAI_CREATED);
          Calendar created = null;
          if (createdObj instanceof Calendar) {
            created = (Calendar) createdObj;
          } else {
            LOGGER.info("Parsing string created date for message at path '{}' because it's stored as string", path);
            try {
              Date date = SAKAI_CREATED_DATE_CONVERSION.parse(String.valueOf(createdObj));
              if (date != null) {
                created = Calendar.getInstance();
                created.setTime(date);
              }
            } catch (ParseException e) {
            }
          }
          
          if (created != null) {
            doc.addField(Content.CREATED_FIELD, created.getTimeInMillis());
          } else {
            LOGGER.warn("Did not index message creation date due to no suitable sakai:message value.");
          }
          
          //index sender's first and last name
          AuthorizableManager am = session.getAuthorizableManager();
          String senderAuthId = (String)content.getProperty("sakai:from");
          Authorizable senderAuth = am.findAuthorizable(senderAuthId);
          if (senderAuth != null) {
            doc.addField("firstName", senderAuth.getProperty("firstName"));
            doc.addField("lastName", senderAuth.getProperty("lastName"));
          }

          doc.addField(IndexingHandler._DOC_SOURCE_OBJECT, content);
          documents.add(doc);

          // index for user,group searching
          String authId = PathUtils.getAuthorizableId(content.getPath());
          Authorizable auth = am.findAuthorizable(authId);
          if (auth == null) {
            LOGGER.info("Unable to find auth (user,group) container for message [{}]; not indexing message for user,group searching", path);
          } else {
            doc = new SolrInputDocument();
            doc.addField("title", content.getProperty("sakai:subject"));
            doc.addField("content", content.getProperty("sakai:body"));

            if (auth.isGroup()) {
              doc.setField("type", "g");
            } else {
              doc.setField("type", "u");
            }

            doc.setField(IndexingHandler._DOC_SOURCE_OBJECT, content);

            // set the path here so that it's the first path found when rendering to the
            // client. the resource indexing service will add all nodes of the path and
            // we want this one to return first in the result processor.
            doc.setField(IndexingHandler.FIELD_PATH, authId);
            doc.setField(IndexingHandler.FIELD_ID, path + AUTH_SUFFIX);

            // set the return to a single value field so we can group it
            doc.setField("returnpath", authId);
            documents.add(doc);
          }
        }
      } catch (StorageClientException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    LOGGER.debug("Got documents {} ", documents);
    return documents;
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
    LOGGER.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty(IndexingHandler.FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (CONTENT_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:(" + ClientUtils.escapeQueryChars(path) + " OR "
          + ClientUtils.escapeQueryChars(path + AUTH_SUFFIX) + ")");
    }
    return retval;
  }

  /**
   * Given the path of a piece of content, determine if the content is "temporary".
   * 
   * @param path The path of the content
   * @return Whether or not the content is stored in a "temporary" path.
   */
  private boolean isTemporaryContent(String path) {
    return !StringUtils.isEmpty(path) && path.contains("/tmp_");
  }
}

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
package org.sakaiproject.nakamura.calendar.search;

import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_EVENT_RT;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_PROFILE_LINK;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_PROPERTY_PREFIX;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_RT;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_EVENT_SIGNUP_PARTICIPANT_RT;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SIGNUP_NODE_RT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingConstants;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Component(immediate = true)
public class CalendarIndexingHandler implements IndexingHandler {

  private static final Logger logger = LoggerFactory
      .getLogger(CalendarIndexingHandler.class);

  @Reference(target = "(type=sparse)")
  private ResourceIndexingService resourceIndexingService;

  private static final Set<String> RESOURCE_TYPES = Sets.newHashSet(SAKAI_CALENDAR_RT,
      SAKAI_CALENDAR_EVENT_RT, SIGNUP_NODE_RT, SAKAI_EVENT_SIGNUP_PARTICIPANT_RT);

  @Activate
  protected void activate(Map<?, ?> props) {
    for (String type : RESOURCE_TYPES) {
      resourceIndexingService.addHandler(type, this);
    }
  }

  @Deactivate
  protected void deactivate(Map<?, ?> props) {
    for (String type : RESOURCE_TYPES) {
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

    List<SolrInputDocument> documents = Lists.newArrayList();
    if (!StringUtils.isBlank(path)) {
      try {
        Session session = repositorySession.adaptTo(Session.class);
        ContentManager cm = session.getContentManager();
        Content content = cm.get(path);

        if (content != null) {
          SolrInputDocument doc = null;

          String resourceType = (String) content
              .getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);
          if (SAKAI_EVENT_SIGNUP_PARTICIPANT_RT.equals(resourceType)) {
            doc = new SolrInputDocument();
            // the default fields are good for the other resource types.
            // SAKAI_EVENT_SIGNUP_PARTICIPANT_RT needs to flatten out the data to search
            // on the profile of the user that signed up.
            Object value = content.getProperty(SAKAI_CALENDAR_PROFILE_LINK);
            if ( value != null ) {
              doc.addField(Authorizable.NAME_FIELD, value);
            }
            doc.addField(_DOC_SOURCE_OBJECT, content);
          } else if ( content.hasProperty(SAKAI_CALENDAR_PROPERTY_PREFIX + "DTSTART")){
            doc = new SolrInputDocument();
            Object value = content.getProperty(SAKAI_CALENDAR_PROPERTY_PREFIX + "DTSTART");
            if ( value != null ) {
              try {
                ISO8601Date d = new ISO8601Date(String.valueOf(value));
                String dateString = d.toString();
                doc.addField("vcal-DTSTART", dateString);
              } catch ( IllegalArgumentException e ) {
                LoggerFactory.getLogger(this.getClass()).warn("Invalid Date object: {}",e.getMessage(),e);
              }
            }
            doc.addField(_DOC_SOURCE_OBJECT, content);
          }
          if (doc != null) {
            documents.add(doc);
          }
        }
      } catch (StorageClientException e) {
        logger.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        logger.warn(e.getMessage(), e);
      }
    }
    logger.debug("Got documents {} ", documents);
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
    logger.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty(FIELD_PATH);
    String resourceType = (String) event.getProperty("resourceType");
    if (RESOURCE_TYPES.contains(resourceType)) {
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(path));
    }
    return retval;
  }

}

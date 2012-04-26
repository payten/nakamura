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
package org.sakaiproject.nakamura.user.search;

import static org.sakaiproject.nakamura.api.user.UserConstants.USER_PROFILE_RESOURCE_TYPE;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Handler to index sections of a profile.
 */
@Component(immediate = true)
public class ProfileIndexingHandler implements IndexingHandler {
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ProfileIndexingHandler.class);

  @Reference
  protected ResourceIndexingService resourceIndexer;

  // ---------- SCR integration ------------------------------------------------
  @Activate
  protected void activate(Map<?, ?> props) {
    resourceIndexer.addHandler(USER_PROFILE_RESOURCE_TYPE, this);
  }

  @Deactivate
  protected void deactivate(Map<?, ?> props) {
    resourceIndexer.removeHandler(USER_PROFILE_RESOURCE_TYPE, this);
  }

  // ---------- IndexingHandler interface --------------------------------------
  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  @Override
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    // We expect the path to the indexed data to follow this pattern:
    // path=a:user1/public/authprofile/aboutme/elements/academicinterests

    String actualPath = String.valueOf(event.getProperty("path"));
    String[] pathParts = StringUtils.split(actualPath, "/", 5);
    // ensure we have at least enough path segments to find the section that was updated
    if (pathParts.length < 3) {
      return null;
    }

    // don't index basic info as a separate profile document since that info is included
    // in the authorizable document
    if (pathParts.length == 4 && "basic".equals(pathParts[3])) {
      return null;
    }

    List<SolrInputDocument> docs = Lists.newArrayList();
    try {
      Session session = repositorySession.adaptTo(Session.class);
      ContentManager cm = session.getContentManager();
      if (pathParts.length == 3) {
        // looks like the sakai/auth-profile node itself is where the update happened so
        // index each of the sections
        String profilePath = StringUtils.join(pathParts, "/", 0, 3);
        Iterator<String> sectionPaths = cm.listChildPaths(profilePath);
        while (sectionPaths.hasNext()) {
          String sectionPath = sectionPaths.next();
          docs.add(processSection(sectionPath, pathParts[0].substring(2), cm));
        }
      } else {
        // updated the section or further down so process just the section
        String sectionPath = StringUtils.join(pathParts, "/", 0, Math.min(pathParts.length, 4));
        docs.add(processSection(sectionPath, pathParts[0].substring(2), cm));
      }
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
    }
    return docs;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  @Override
  public Collection<String> getDeleteQueries(RepositorySession respositorySession,
      Event event) {
    String actualPath = String.valueOf(event.getProperty("path"));
    String[] pathParts = StringUtils.split(actualPath, "/", 5);
    String sectionPath = StringUtils.join(pathParts, "/", 0, Math.min(pathParts.length, 4));
    return ImmutableList.of("id:" + ClientUtils.escapeQueryChars(sectionPath));
  }

  /**
   * Process a section by collecting the "value" field from each of its elements.
   * 
   * @param sectionPath
   * @param cm
   * @return
   * @throws StorageClientException
   */
  private SolrInputDocument processSection(String sectionPath, String authId,
      ContentManager cm) throws StorageClientException, AccessDeniedException {
    Iterator<Content> sections = cm.listChildren(sectionPath + "/elements");
    SolrInputDocument doc = new SolrInputDocument();
    doc.setField("id", sectionPath);
    // add the user id as the first path
    doc.setField("path", authId);
    // add the indexed section as the second path
    doc.addField("path", sectionPath);
    // we have to set the _source field so default fields are added automatically.
    // it only matters that we set a Content object to _source
    doc.setField("_source", cm.get(sectionPath));
    doc.setField("type", "u");
    doc.setField("resourceType", "profile");
    while (sections.hasNext()) {
      Content section = sections.next();
      Object value = section.getProperty("value");
      if (value != null) {
        doc.addField("profile", value);
      }
    }
    return doc;
  }
}

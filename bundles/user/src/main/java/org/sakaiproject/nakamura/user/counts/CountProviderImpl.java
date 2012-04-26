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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.sakaiproject.nakamura.api.user.UserConstants.*;

@Component(metatype = true)
@Service
public class CountProviderImpl implements CountProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CountProviderImpl.class);

  @Reference
  protected SolrServerService solrSearchService;

  @Reference
  protected Repository repository;

  @Property(intValue = 30)
  public static final String UPDATE_INTERVAL_MINUTES = "sakai.countProvider.updateIntervalMinutes";

  private long updateIntervalMinutes;

  private GroupMembershipCounter groupMembershipCounter = new GroupMembershipCounter();

  private ConnectionsCounter contactsCounter = new ConnectionsCounter();

  private ContentCounter contentCounter = new ContentCounter();

  private GroupMembersCounter groupMembersCounter = new GroupMembersCounter();

  public void update(Authorizable authorizable, Session session)
      throws AccessDeniedException, StorageClientException {
    if (authorizable == null || IGNORE_AUTHIDS.contains(authorizable.getId())) {
      return;
    }

    internalUpdateCountProperty(authorizable, CONTENT_ITEMS_PROP, session, false);
    if (authorizable instanceof User) {
      internalUpdateCountProperty(authorizable, CONTACTS_PROP, session, false);
      internalUpdateCountProperty(authorizable, GROUP_MEMBERSHIPS_PROP, session, false);
    } else if (authorizable instanceof Group) {
      internalUpdateCountProperty(authorizable, GROUP_MEMBERS_PROP, session, true);
    }

  }

  @Override
  public void updateCountProperty(Authorizable authorizable, String propertyName, Session session)
      throws AccessDeniedException, StorageClientException {
    if (authorizable == null || IGNORE_AUTHIDS.contains(authorizable.getId())) {
      return;
    }
    internalUpdateCountProperty(authorizable, propertyName, session, true);
  }

  private void internalUpdateCountProperty(Authorizable authorizable, String propertyName,
                                           Session session, boolean saveChanges)
      throws AccessDeniedException,
      StorageClientException {
    if (authorizable == null || IGNORE_AUTHIDS.contains(authorizable.getId())) {
      return;
    }
    AuthorizableManager authorizableManager = session.getAuthorizableManager();

    if (CONTENT_ITEMS_PROP.equals(propertyName)) {
      authorizable.setProperty(CONTENT_ITEMS_PROP, getContentCount(authorizable, authorizableManager));
    } else {
      if (authorizable instanceof User) {
        if (CONTACTS_PROP.equals(propertyName)) {
          authorizable.setProperty(CONTACTS_PROP, getContactsCount(authorizable, authorizableManager));
        } else if (GROUP_MEMBERSHIPS_PROP.equals(propertyName)) {
          authorizable.setProperty(GROUP_MEMBERSHIPS_PROP, getGroupsCount(authorizable, authorizableManager));
        }
      } else if (authorizable instanceof Group) {
        if (GROUP_MEMBERS_PROP.equals(propertyName)) {
          authorizable.setProperty(GROUP_MEMBERS_PROP, getMembersCount((Group) authorizable, authorizableManager));
        }
      }
    }

    if (saveChanges) {
      long lastUpdate = System.currentTimeMillis();
      authorizable.setProperty(COUNTS_LAST_UPDATE_PROP, lastUpdate);
      if (LOG.isDebugEnabled()) {
        if (authorizable instanceof User) {
          LOG.debug("update User authorizable: {} with {}={}, {}={}, {}={}",
              new Object[]{authorizable.getId(),
                  CONTENT_ITEMS_PROP, authorizable.getProperty(CONTENT_ITEMS_PROP),
                  CONTACTS_PROP, authorizable.getProperty(CONTACTS_PROP),
                  GROUP_MEMBERSHIPS_PROP, authorizable.getProperty(GROUP_MEMBERSHIPS_PROP),
                  COUNTS_LAST_UPDATE_PROP, lastUpdate});
        } else if (authorizable instanceof Group) {
          LOG.debug("update Group authorizable: {} with {}={}, {}={}", new Object[]{
              authorizable.getId(),
              CONTENT_ITEMS_PROP, authorizable.getProperty(CONTENT_ITEMS_PROP),
              GROUP_MEMBERS_PROP, authorizable.getProperty(GROUP_MEMBERS_PROP),
              COUNTS_LAST_UPDATE_PROP, lastUpdate});
        }
      }
      authorizableManager.updateAuthorizable(authorizable, false);
    }
  }

  public long getUpdateIntervalMinutes() {
    return this.updateIntervalMinutes;
  }

  private int getMembersCount(Group group, AuthorizableManager authorizableManager) throws AccessDeniedException,
      StorageClientException {
    return groupMembersCounter.count(group, authorizableManager);
  }

  private int getGroupsCount(Authorizable au, AuthorizableManager authorizableManager)
      throws AccessDeniedException, StorageClientException {
    return groupMembershipCounter.count(au, authorizableManager);
  }

  private int getContentCount(Authorizable au, AuthorizableManager authorizableManager) throws AccessDeniedException,
      StorageClientException {
    return contentCounter.countExact(au, authorizableManager, solrSearchService);
  }

  private int getContactsCount(Authorizable au, AuthorizableManager authorizableManager)
      throws AccessDeniedException, StorageClientException {
    return contactsCounter.count(au, authorizableManager);
  }


  // ---------- SCR integration ---------------------------------------------
  @Activate
  public void activate(Map<String, Object> properties) throws StorageClientException,
      AccessDeniedException {
    modify(properties);
  }

  @Modified
  public void modify(Map<String, Object> properties) throws StorageClientException,
      AccessDeniedException {
    updateIntervalMinutes = PropertiesUtil.toLong(properties.get(UPDATE_INTERVAL_MINUTES), 30);
  }


}

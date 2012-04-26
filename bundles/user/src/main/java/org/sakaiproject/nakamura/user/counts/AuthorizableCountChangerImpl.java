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

package org.sakaiproject.nakamura.user.counts;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.user.AuthorizableCountChanger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

@Component(metatype = true)
@Service
public class AuthorizableCountChangerImpl implements AuthorizableCountChanger {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizableCountChangerImpl.class);

  @Reference
  private Repository repository;

  @SuppressWarnings("unchecked")
  @Override
  public void notify(String propertyName, String authorizableID) {
    this.notify(propertyName, Arrays.asList(authorizableID));
  }

  @Override
  public void notify(String propertyName, Collection<String>... authorizableIDs) {
    Session adminSession = null;
    try {
      adminSession = this.repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      for (Collection<String> list : authorizableIDs) {
        for (String id : list) {
          Authorizable authz = authorizableManager.findAuthorizable(id);
          if (authz == null || CountProvider.IGNORE_AUTHIDS.contains(authz.getId())) {
            continue; // skip the immutable system authzs
          }
          if (authz.hasProperty(propertyName)) {
            authz.removeProperty(propertyName);
            authorizableManager.updateAuthorizable(authz, false);
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("Removed {} prop from authorizable {}", new String[]{propertyName, id});
            }
          }
        }
      }
    } catch (AccessDeniedException e) {
      LOGGER.error("Error removing count property from authorizable", e);
    } catch (ClientPoolException e) {
      LOGGER.error("Error removing count property from authorizable", e);
    } catch (StorageClientException e) {
      LOGGER.error("Error removing count property from authorizable", e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("Error logging out of admin session", e);
        }
      }
    }
  }
}

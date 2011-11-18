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
 * specific language governing permissions and limitations under
 * the License.
 */
package org.sakaiproject.nakamura.authindex;

import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.osgi.service.component.ComponentException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for triggering indexing of all authorizables. Indexing happens when the
 * component is activated.
 */
@Component
public class AuthorizableIndexer {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizableIndexer.class);

  @Reference
  private Repository repo;

  @Activate
  protected void activate(Map<?, ?> props) {
    LOGGER.info("Starting to index all authorizables...");
    Session session = null;
    try {
      session = repo.loginAdministrative();
      session.getAuthorizableManager().triggerRefreshAll();
      LOGGER.info("***** Indexing of all authorizables triggered. Watch the logs for completion of indexing *****");
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      throw new ComponentException(e.getMessage(), e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (Exception e) {
          // nothing we can do; just ignore this
        }
      }
    }
  }
}

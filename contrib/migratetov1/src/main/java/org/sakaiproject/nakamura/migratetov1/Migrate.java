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
package org.sakaiproject.nakamura.migratetov1;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.Binary;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.api.resource.ResourceResolverFactory;


/**
 *
 */
@Component
public class Migrate {
  private Logger LOGGER = LoggerFactory.getLogger(Migrate.class);

  @Reference
    private Repository repository;

  @Reference
    private ResourceResolverFactory resourceResolverFactory;

  private void fixAuthorizableTags() throws Exception
  {
    Session session = null;
    try {
      session = repository.loginAdministrative();
      AuthorizableManager am = session.getAuthorizableManager();

      javax.jcr.Session jcrSession = resourceResolverFactory
        .getResourceResolver(ImmutableMap.of(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, (Object)session))
        .adaptTo(javax.jcr.Session.class);

      Iterator<Authorizable> it;

      for (String type : new String[] { "u", "g" }) {
        it = am.findAuthorizable("type", type, Authorizable.class);

        while (it.hasNext()) {
          Authorizable au = it.next();

          try {
            fixAuthorizableTags(au, am, jcrSession);
          } catch (Exception e) {
            LOGGER.error ("Error fixing authorizable '{}': {}", e, au);
            e.printStackTrace();
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error ("Failure when fixing authorizable tags.  Cause follows: {}", e);
      e.printStackTrace();
    }  finally {
      if (session != null) {
        session.logout();
      }
    }
  }


  private void fixAuthorizableTags(Authorizable au, AuthorizableManager am, javax.jcr.Session jcrSession)
    throws Exception
  {
    LOGGER.info ("Fixing {}", au);

    String[] taguuids = (String[]) au.getProperty("sakai:tag-uuid");

    if (taguuids != null) {
      List<String> tags = new ArrayList<String>();
      for (String uuid : taguuids) {
        LOGGER.info ("UUID is: {}", uuid);

        String tagName = jcrSession.getNodeByIdentifier(uuid).getProperty("sakai:tag-name").getString();
        LOGGER.info ("Tag is: {}", tagName);

        tags.add(tagName);
      }

      au.setProperty("sakai:tags", tags.toArray(new String[tags.size()]));
      am.updateAuthorizable(au);
    }
  }


  @Activate
    protected void activate(ComponentContext componentContext) {
    try {
      LOGGER.info("Migrating!");
      fixAuthorizableTags();
    } catch (Exception e) {
      LOGGER.error("Caught a top-level error: {}", e);
      e.printStackTrace();
    }
  }
}

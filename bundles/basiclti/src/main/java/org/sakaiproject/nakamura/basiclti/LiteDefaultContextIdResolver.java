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
package org.sakaiproject.nakamura.basiclti;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.basiclti.LiteBasicLTIContextIdResolver;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

/**
 * The default mapping strategy will be to simply return the {@link Content#getPath()} as
 * the LTI context_id. For example: <code>/sites/12345</code>.
 * 
 * If the {@link Content} has a special property, <code>lti_context_id</code>, then use
 * the value of that property as the context_id. See: {@link #LTI_CONTEXT_ID}.
 * 
 */
@Component(immediate = true, metatype = true)
@Service
public class LiteDefaultContextIdResolver implements LiteBasicLTIContextIdResolver {
  private static final Logger LOG = LoggerFactory
      .getLogger(LiteDefaultContextIdResolver.class);

  /**
   * The {@link Content} property key used to store an LTI context_id.
   */
  @Property(value = "lti_context_id", name = "LTI_CONTEXT_ID", label = "key", description = "The Content property key used to store an LTI context_id.")
  public static final String LTI_CONTEXT_ID = LiteDefaultContextIdResolver.class
      .getName() + ".key";
  protected String key = "lti_context_id";

  /**
   * {@inheritDoc}
   * 
   * @see {@link LiteBasicLTIContextIdResolver#resolveContextId(Content)}
   */
  public String resolveContextId(final Content node, final String groupId,
      final Session session) throws AccessDeniedException, StorageClientException {
    LOG.debug("resolveContextId(Content {}, String {}, Session session)", node, groupId);
    if (node == null) {
      throw new IllegalArgumentException("Content cannot be null");
    }
    if (session == null) {
      throw new IllegalArgumentException("Session cannot be null");
    }

    final AuthorizableManager authManager = session.getAuthorizableManager();

    // default return value
    String contextId = node.getPath();

    // check the alternate key for a context ID
    if (node.hasProperty(key)) {
      // we have a special context_id we can use
      contextId = (String) node.getProperty(key);
    } else {
      if (groupId != null) {
        // obtaining the group causes a security check; also the group is used later to
        // check the sakai:cle-site prop.
        final Group group = (Group) authManager.findAuthorizable(groupId);
        LOG.debug("group = {}", group);

        /*
         * If we are using a Group, we check the group's node to see if it has one of the
         * optional properties that control LTI_CONTEXT_ID: "lti_context_id" or
         * "sakai:cle-site". The property "lti_context_id" will take precedence over
         * "sakai:cle-site". They are used to manually control LTI mapping to particular
         * contexts and are passed straight through verbatim.
         */
        if (group != null) {
          // by default, just use the groupID that was submitted
          contextId = groupId;

          // LTI_CONTEXT_ID takes precedence over "sakai:cle-site" property
          if (group.hasProperty(key)) {
            // we have a special context_id we can use
            final String groupContextId = (String) group.getProperty(key);
            if (groupContextId != null && !"".equals(groupContextId)) {
              contextId = groupContextId;
            }
          } else {
            // try "sakai:cle-site" property next
            final String sakaiSite = (String) group.getProperty("sakai:cle-site");
            if (sakaiSite != null && !"".equals(sakaiSite)) {
              contextId = sakaiSite;
            }
          }
        }
      }
    }
    LOG.debug("Using context id '{}' for node: {}", contextId, node.getPath());
    return contextId;
  }

  protected void activate(ComponentContext context) {
    @SuppressWarnings("rawtypes")
    final Dictionary props = context.getProperties();
    final String newKey = PropertiesUtil.toString(props.get(LTI_CONTEXT_ID), key);
    if (!"".equals(newKey)) {
      key = newKey;
    }
  }
}

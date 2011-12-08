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
package org.sakaiproject.nakamura.email.outgoing;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.message.AbstractMessageRoute;
import org.sakaiproject.nakamura.api.message.LiteMessageRouter;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import javax.jcr.RepositoryException;

@Service
@Component
public class LiteSmtpRouter implements LiteMessageRouter {
  private static final Logger LOG = LoggerFactory.getLogger(LiteSmtpRouter.class);

  /**
   * The Content Repository we access.
   *
   */
  @Reference
  private Repository contentRepository;

  @Reference
  private ProfileService profileService;

  @Reference
  private BasicUserInfoService basicUserInfo;

  @Reference
  private SlingRepository slingRepo;

  public LiteSmtpRouter() {
  }

  LiteSmtpRouter(Repository contentRepository, SlingRepository slingRepository, ProfileService profileService) {
    this.contentRepository = contentRepository;
    this.slingRepo = slingRepository;
    this.profileService = profileService;
  }

  public int getPriority() {
    return 0;
  }

  public void route(Content message, MessageRoutes routing) {
    Collection<MessageRoute> rewrittenRoutes = new ArrayList<MessageRoute>();
    Iterator<MessageRoute> routeIterator = routing.iterator();
    while (routeIterator.hasNext()) {
      MessageRoute route = routeIterator.next();
      String rcpt = route.getRcpt();
      String transport = route.getTransport();

      LOG.debug("Checking Message Route {} ",route);
      boolean rcptNotNull = rcpt != null;
      boolean transportNullOrInternal = transport == null || "internal".equals(transport);

      if (rcptNotNull && transportNullOrInternal) {
        // check the user's profile for message delivery preference. if the
        // preference is set to smtp, change the transport to 'smtp'.
        try {
          Session session = contentRepository.loginAdministrative();
          Authorizable au = session.getAuthorizableManager().findAuthorizable(rcpt);
          if (au != null) {
            String profilePath = LitePersonalUtils.getProfilePath(au.getId());
            Content profileNode = session.getContentManager().get(profilePath);
            boolean smtpPreferred = isPreferredTransportSmtp(profileNode);
            boolean smtpMessage = isMessageTypeSmtp(message);
            if (smtpPreferred || smtpMessage) {
              LOG.debug("Message is an SMTP Message, getting email address for the authorizable {}", au.getId());
              String rcptEmailAddress;
              if (au instanceof Group) {
                // Can just use the ID of the group, as the members will
                // be looked up and email sent to them
                // TODO: If a group can have an email address sometime in the
                //  future, remove this check
                rcptEmailAddress = au.getId();
              } else {
                rcptEmailAddress = OutgoingEmailUtils.getEmailAddress(au, session, basicUserInfo, profileService, slingRepo);
              }

              if (StringUtils.isBlank(rcptEmailAddress)) {
                LOG.warn("Can't find a primary email address for [{}]; smtp message will not be sent to authorizable.", rcpt);
              } else {
                AbstractMessageRoute smtpRoute = new AbstractMessageRoute(
                    MessageConstants.TYPE_SMTP + ":" + rcptEmailAddress) {
                };
                rewrittenRoutes.add(smtpRoute);
                routeIterator.remove();
              }
            }
          }
        } catch (RepositoryException e) {
          LOG.error(e.getMessage());
        } catch (ClientPoolException e) {
          LOG.error(e.getMessage());
        } catch (StorageClientException e) {
          LOG.error(e.getMessage());
        } catch (AccessDeniedException e) {
          LOG.error(e.getMessage());
        }
      }
    }
    routing.addAll(rewrittenRoutes);

    LOG.debug("Final Routing is [{}]", Arrays.toString(routing.toArray(new MessageRoute[routing.size()])));
  }

  private boolean isMessageTypeSmtp(Content message) throws RepositoryException {
    boolean isSmtp = false;

    if (message != null && message.hasProperty(MessageConstants.PROP_SAKAI_TYPE)) {
      String prop = (String) message.getProperty(MessageConstants.PROP_SAKAI_TYPE);
      isSmtp = MessageConstants.TYPE_SMTP.equals(prop);
    }


    return isSmtp;
  }

  private boolean isPreferredTransportSmtp(Content profileNode) throws RepositoryException {
    boolean prefersSmtp = false;

    if (profileNode != null) {
      // Get this user's preferred message transport
      String transport = LitePersonalUtils.getPreferredMessageTransport(profileNode);
      prefersSmtp = MessageConstants.TYPE_SMTP.equals(transport);
    }

    return prefersSmtp;
  }
}

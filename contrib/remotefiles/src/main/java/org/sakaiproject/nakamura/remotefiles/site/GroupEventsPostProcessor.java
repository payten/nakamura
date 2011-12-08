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
package org.sakaiproject.nakamura.remotefiles.site;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
//import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.site.SiteService.SiteEvent;
import org.sakaiproject.nakamura.remotefiles.RemoteFilesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component(enabled = true, immediate = true, metatype = true)
@Service(value = EventHandler.class)
@Properties(value = {
    @Property(name = "service.vendor", value = "Sakai Foundation"),
    @Property(name = "service.description", value = "Provides a place to respond when sites are created and memberships updated"),
    @Property(name = "event.topics", value = "org/sakaiproject/nakamura/api/site/event/created") })
// SiteService.SiteEvent.created.getTopic()
public class GroupEventsPostProcessor implements EventHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GroupEventsPostProcessor.class);

  @Reference
  RemoteFilesRepository remoteFilesRepository;

  @Activate
  protected void activate(Map<?, ?> props) {
    //sitesReplacement = PropertiesUtil.toString(SITES_REPLACEMENT, DEFAULT_SITES_REPLACEMENT);
  }

  public void handleEvent(Event event) {
    String sitePath = (String) event.getProperty(SiteEvent.SITE);
    String userId = (String) event.getProperty(SiteEvent.USER);
    try {
      remoteFilesRepository.createGroup(sitePath, userId);
      remoteFilesRepository.createDirectory(userId, null, "/sites", sitePath);
    } catch (Exception e1) {
      LOGGER.warn("failed to create remote files group when creating site: " + e1.getMessage());
    }
  }

}

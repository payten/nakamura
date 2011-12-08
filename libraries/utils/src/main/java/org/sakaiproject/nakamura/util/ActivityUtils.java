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
package org.sakaiproject.nakamura.util;

import com.google.common.collect.Maps;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.Hashtable;
import java.util.Map;

public class ActivityUtils {

  
  /**
   * Post an activity event. processed by activity listeners.
   * @param eventAdmin 
   * @param userId the userID performing the activity
   * @param path the path to the node the activity is associated with
   * @param appId the app Id (default is "Content" if null)
   * @param templateId the template Id (default is "default" if null)
   * @param type the type (default is content if null)
   * @param message the message ( default is NONE if null)
   * @param attributes attributes, ignored if null.
   */
  public static void postActivity(EventAdmin eventAdmin, String userId, String path, String appId, String templateId, String type, String message, Map<String, Object> attributes ) {
    Map<String, Object> finalAttributes = Maps.newHashMap();
    if ( attributes != null) {
      finalAttributes.putAll(attributes);
    }
    if ( appId == null ) {
      appId = "Content";
    }
    if ( templateId == null ) {
      templateId = "default";
    }
    if ( type == null ) {
      type = "content";
    }
    if ( message == null ) {
      message = "NONE";
    }
    finalAttributes.put("sakai:activity-appid", appId);
    finalAttributes.put("sakai:activity-appid", templateId);
    finalAttributes.put("sakai:activity-type", type);
    finalAttributes.put("sakai:activityMessage", message);
    Hashtable<String, Object> properties = new Hashtable<String, Object>();
    properties.put("path", path);
    properties.put("userid", userId);
    properties.put("attributes", finalAttributes);
    eventAdmin.postEvent(new Event("org/sakaiproject/nakamura/activity/POSTED", (Map) properties));
  }
}

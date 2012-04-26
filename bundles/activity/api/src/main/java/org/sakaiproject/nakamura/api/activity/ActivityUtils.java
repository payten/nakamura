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
package org.sakaiproject.nakamura.api.activity;

import static org.sakaiproject.nakamura.api.activity.ActivityConstants.EVENT_TOPIC;

import org.apache.commons.lang.StringUtils;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.sakaiproject.nakamura.util.PathUtils;

import com.google.common.collect.Maps;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

/**
 *
 */
public class ActivityUtils {

  private static SecureRandom random = null;

  @SuppressWarnings("rawtypes")
  public static Event createEvent(String user, String activityItemPath) {
    final Dictionary<String, String> map = new Hashtable<String, String>(1);
    map.put("userid", user);
    map.put(ActivityConstants.EVENT_PROP_PATH, activityItemPath);
    return new Event(EVENT_TOPIC, (Dictionary) map);
  }

  /**
   * Returns the path to the activity feed for a user.
   *
   * @param user
   * @return
   */
  public static String getUserFeed(String user) {
    return LitePersonalUtils.getPrivatePath(user) + "/"
        + ActivityConstants.ACTIVITY_FEED_NAME;
  }

  /**
   * Get the path from an activity id.
   *
   * @param id
   *          The ID for an activity.
   * @param startPath
   *          The starting path.
   * @return Given an id '2010-01-21-09-randombit' and startPath '/foo/bar' this will
   *         return '/foo/bar/2010/01/21/09/2010-01-21-09-randombit'.
   */
  public static String getPathFromId(String id, String startPath) {
    String[] hashes = StringUtils.split(id, '-');
    StringBuilder sb;

    if (startPath == null) {
      sb = new StringBuilder();
    } else {
      startPath = PathUtils.normalizePath(startPath);
      sb = new StringBuilder(startPath);
    }

    for (int i = 0; i < (hashes.length - 1); i++) {
      sb.append("/").append(hashes[i]);
    }
    return sb.append("/").append(id).toString();

  }

  /**
   * @return Creates a unique path to an activity in the form of 2010-01-21-09-randombit
   */
  public static String createId() {
    Calendar c = Calendar.getInstance();

    String[] vals = new String[4];
    vals[0] = "" + c.get(Calendar.YEAR);
    vals[1] = StringUtils.leftPad("" + (c.get(Calendar.MONTH) + 1), 2, "0");
    vals[2] = StringUtils.leftPad("" + c.get(Calendar.DAY_OF_MONTH), 2, "0");
    vals[3] = StringUtils.leftPad("" + c.get(Calendar.HOUR_OF_DAY), 2, "0");

    StringBuilder id = new StringBuilder();

    for (String v : vals) {
      id.append(v).append("-");
    }

    byte[] bytes = new byte[20];
    String randomHash = "";
    try {
      if (random == null) {
        random = SecureRandom.getInstance("SHA1PRNG");
      }
      random.nextBytes(bytes);
      randomHash = Arrays.toString(bytes);
      randomHash = org.sakaiproject.nakamura.util.StringUtils
          .sha1Hash(randomHash);
    } catch (NoSuchAlgorithmException e) {
    } catch (UnsupportedEncodingException e) {
    }

    id.append(randomHash);
    return id.toString();
  }

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
    finalAttributes.put("sakai:activity-templateid", templateId);
    finalAttributes.put("sakai:activity-type", type);
    finalAttributes.put("sakai:activityMessage", message);
    Map<String, Object> properties = Maps.newHashMap();
    properties.put("path", path);
    properties.put("userid", userId);
    properties.put("attributes", finalAttributes);
    eventAdmin.postEvent(new Event("org/sakaiproject/nakamura/activity/POSTED", properties));
  }
}

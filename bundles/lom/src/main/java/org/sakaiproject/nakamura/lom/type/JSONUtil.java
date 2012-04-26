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

package org.sakaiproject.nakamura.lom.type;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;

import java.util.Iterator;

/**
 * This class is to help search element in json object, ignoring case sensitive in element names.
 */
public class JSONUtil {
  
  public static JSONObject getJSONObject(JSONObject json, String elementName) {
    if (json.optJSONObject(elementName) != null) {
      return json.optJSONObject(elementName);
    }
    Iterator<String> names = json.keys();
    elementName = elementName.toLowerCase();
    if (names != null) {
      while(names.hasNext()) {
        String s = names.next();
        if (s.length() < elementName.length()) {
          continue;
        }
        if (elementName.equalsIgnoreCase(s) || s.toLowerCase().endsWith(":" + elementName.toLowerCase())) {
          return json.optJSONObject(s);
        }
       }
    }
    return null;
  }
  
  public static JSONArray getJSONArray(JSONObject json, String elementName) {
    if (json.optJSONArray(elementName) != null) {
      return json.optJSONArray(elementName);
    }
    Iterator<String> names = json.keys();
    if (names != null) {
      while(names.hasNext()) {
        String s = names.next();
        if (s.length() < elementName.length()) {
          continue;
        }
        if (elementName.equalsIgnoreCase(s) || s.toLowerCase().endsWith(":" + elementName.toLowerCase())) {
          return json.optJSONArray(s);
        }
       }
    }
    return null;
  }
  
  public static String getStringValue(JSONObject json, String elementName) {
    if (getJSONObject(json, elementName) != null
        || getJSONArray(json, elementName) != null) {
      return null;
    }
    if (json.optString(elementName) != null && !("".equals(json.optString(elementName)))) {
      return json.optString(elementName);
    }
    Iterator<String> names = json.keys();
    if (names != null) {
      while(names.hasNext()) {
        String s = names.next();
        if (s.length() < elementName.length()) {
          continue;
        }
        if (elementName.equalsIgnoreCase(s) || s.toLowerCase().endsWith(":" + elementName.toLowerCase())) {
          return json.optString(s);
        }
       }
    }
    return null;
  }
}

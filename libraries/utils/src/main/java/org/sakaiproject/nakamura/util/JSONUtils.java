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

package org.sakaiproject.nakamura.util;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JSONUtils {

  public static boolean arrayContains(JSONArray array, String value) throws JSONException {
    for ( int i = 0; i < array.length(); i++ ) {
      String s = array.getString(i);
      if ( s.equals(value)) {
        return true;
      }
    }
    return false;
  }

  public static void stripJCRNodes(JSONObject object) throws JSONException {
    Iterator<String> keys = object.keys();
    List<String> toRemove = new ArrayList<String>();
    while (keys.hasNext()) {
      String key = keys.next();
      Object child = object.get(key);
      if (child instanceof JSONObject) {
        stripJCRNodes((JSONObject) child);
      } else if (child instanceof JSONArray) {
        JSONArray array = (JSONArray) child;
        for (int i = 0; i < array.length(); i++) {
          Object element = array.get(i);
          if (element instanceof JSONObject) {
            stripJCRNodes((JSONObject) element);
          }
        }
      } else {
        if (JcrUtils.isJCRProperty(key)) {
          toRemove.add(key);
        }
      }
    }
    for (String key : toRemove) {
      object.remove(key);
    }
  }

}

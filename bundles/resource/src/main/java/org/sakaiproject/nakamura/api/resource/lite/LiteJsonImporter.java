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
package org.sakaiproject.nakamura.api.resource.lite;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.RemoveProperty;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class LiteJsonImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteJsonImporter.class);
  private static Map<String, Class<?>> TYPES = null;

  static {
    Builder<String, Class<?>> b = ImmutableMap.builder();
    b.put("TypeInteger", Integer.class);
    b.put("TypeLong", Long.class);
    b.put("TypeDouble", Double.class);
    b.put("TypeBigDecimal", BigDecimal.class);
    b.put("TypeDate", Calendar.class);
    b.put("TypeString", String.class);
    b.put("TypeBoolean", Boolean.class);
    TYPES = b.build();
  }
  
  /**
   * Import <code>json</code> to <code>path</code>. <code>continueIfExists</code> has to
   * be true before <code>replaceProperties</code>, <code>removeTree</code> or
   * <code>merge</code> are considered.
   *
   * @param contentManager
   *          The content manager to perform operations with.
   * @param json
   *          The data to persist.
   * @param path
   *          The path where the data is persisted.
   * @param continueIfExists
   *          Whether to continue if data exists at <code>path</code>.
   * @param replaceProperties
   *          Whether to replace existing properties.
   * @param removeTree
   *          Whether to remove the tree at <code>path</code> before proceeding.
   * @param merge
   *          Whether to merge <code>json</code> with existing content or to replace the
   *          content at <code>path</code> with <code>json</code>.
   * @param accessControlManager
   *          ACL manager to use when importing content.
   * @param withTouch
   *          Whether to update the modified time of the content when importing.
   * @throws JSONException
   * @throws StorageClientException
   * @throws AccessDeniedException
   *
   * @todo Refactor this method and like methods with the following considerations:
   *  <ul>
   *    <li>create non-overlapping values for parameters:
   *      <ul>
   *        <li>// create pattern by picking 2^n values.</li>
   *        <li>int CONTINUE = 1;</li>
   *        <li>int REPLACE_PROPS = 2;</li>
   *        <li>int REMOVE_TREE = 4;</li>
   *        <li>int MERGE = 8;</li>
   *      </ul>
   *    </li>
   *    <li>accept a bitmap for boolean parameters:
   *      <ul>
   *        <li>e.g. CONTINUE | REPLACE_PROPS | MERGE</li>
   *        <li>e.g. CONTINUE | REPLACE_PROPS</li>
   *      </ul>
   *    </li>
   *    <li>get rid of boolean parameters for bitmap</li>
   *  </ul>
   */
  public void importContent(ContentManager contentManager, JSONObject json, String path,
      boolean continueIfExists, boolean replaceProperties, boolean removeTree, boolean merge,
      AccessControlManager accessControlManager, boolean withTouch) throws JSONException,
      StorageClientException, AccessDeniedException {
    if (!continueIfExists && contentManager.get(path) != null) {
      LOGGER.debug("continueIfExists=false and path exists, so discontinuing JSON import to {}", path);
      return;
    }
    if ( removeTree ) {
      for ( Iterator<String> i = contentManager.listChildPaths(path); i.hasNext(); ) {
        String childPath = i.next();
        LOGGER.debug("Deleting {} ",childPath);
        contentManager.delete(childPath, true);
        LOGGER.debug("Done Deleting {} ",childPath);
      }
    }
    internalImportContent(contentManager, json, path, !merge, replaceProperties, accessControlManager, withTouch);
  }

  public void importContent(ContentManager contentManager, JSONObject json, String path,
      boolean continueIfExists, boolean replaceProperties, boolean removeTree,
      AccessControlManager accessControlManager) throws JSONException,
      StorageClientException, AccessDeniedException {
    importContent(contentManager, json, path, continueIfExists, replaceProperties, removeTree, true, accessControlManager, true);
  }

  public void internalImportContent(ContentManager contentManager, JSONObject json,
      String path, boolean replace, boolean replaceProperties,
      AccessControlManager accessControlManager) throws JSONException,
      StorageClientException, AccessDeniedException {
    internalImportContent(contentManager, json, path, replace, replaceProperties, accessControlManager, true);
  }

  public void internalImportContent(ContentManager contentManager, JSONObject json,
      String path, boolean replace, boolean replaceProperties,
      AccessControlManager accessControlManager, boolean withTouch) throws JSONException,
      StorageClientException, AccessDeniedException {

    // delete absent paths if we're replacing content
    if (replace) {
      Iterator<String> childPaths = contentManager.listChildPaths(path);
      while (childPaths.hasNext()) {
        String childPath = childPaths.next();
        // check that the path isn't an object. we only have to prune for missing objects.
        String nodeName = PathUtils.lastElement(childPath);
        if (json.optJSONObject(nodeName) == null) {
          // do not delete the child (regardless of its type) if it is said to be ignored.
          if (json.opt(String.format("%s@Ignore", nodeName)) == null) {
            contentManager.delete(childPath, true);
          }
        }
      }
    }

    Iterator<String> keys = json.keys();
    Map<String, Object> properties = new HashMap<String, Object>();
    List<AclModification> modifications = Lists.newArrayList();
    while (keys.hasNext()) {

      String key = keys.next();
      if (!JcrUtils.isJCRProperty(key)) {
        Object obj = json.get(key);

        String pathKey = getPathElement(key);
        // KERN-2738 this importer needs to support Sling's someProp@TypeHint=Boolean convention
        if (key.endsWith("@TypeHint") && json.has(pathKey)) {
          key = pathKey + "@" + "Type" + obj.toString();
          obj = json.get(pathKey);
        }
        Class<?> typeHint = getElementType(key);

        if (obj instanceof JSONObject) {
          if ( key.endsWith("@grant")) {
            JSONObject acl = (JSONObject) obj;
            int bitmap = getPermissionBitMap(acl.getJSONArray("permission"));
            Operation op = getOperation(acl.getString("operation"));
            modifications.add(new AclModification(AclModification.grantKey(pathKey), bitmap, op));
          } else if ( key.endsWith("@deny")) {
            JSONObject acl = (JSONObject) obj;
            int bitmap = getPermissionBitMap(acl.getJSONArray("permission"));
            Operation op = getOperation(acl.getString("operation"));
            modifications.add(new AclModification(AclModification.denyKey(pathKey), bitmap, op));
          } else if ( key.endsWith("@Delete") ) {
            contentManager.delete(path + "/" + pathKey, true);
          } else if (key.endsWith("@Ignore")) {
            // we're simply ignoring the subtree here, no need to do anything, just don't recurse.
          } else {
            // need to do somethingwith delete here
            internalImportContent(contentManager, (JSONObject) obj, path + "/" + pathKey,
                replace, replaceProperties, accessControlManager, withTouch);
          }
        } else if (obj instanceof JSONArray) {
          if ( key.endsWith("@Delete") ) {
            properties.put(pathKey, new RemoveProperty());
          } else if (key.endsWith("@Ignore")) {
            // we're simply ignoring the array here. no need to do anything.
          } else {
            // This represents a multivalued property
            JSONArray arr = (JSONArray) obj;
            properties.put(pathKey, getArray(arr, typeHint));
          }
        } else {
          if ( key.endsWith("@Delete") ) {
            properties.put(pathKey, new RemoveProperty());
          } else if (key.endsWith("@Ignore")) {
            // we're simply ignoring the property here. no need to do anything.
          } else {
            properties.put(pathKey, getObject(obj, typeHint));
          }
        }
      }
    }
    Content content = contentManager.get(path);
    if (content == null) {
      if (replace) {
        contentManager.replace(new Content(path, properties), withTouch);
      } else {
        contentManager.update(new Content(path, properties), withTouch);
      }
      LOGGER.debug("Created Node {} {}",path,properties);
    } else {
      for (Entry<String, Object> e : properties.entrySet()) {
        if ( replaceProperties || !content.hasProperty(e.getKey())) {
          LOGGER.debug("Updated Node {} {} {} ",new Object[]{path,e.getKey(), e.getValue()});
          content.setProperty(e.getKey(), e.getValue());
        }
      }
      if (replace) {
        contentManager.replace(content, withTouch);
      } else {
        contentManager.update(content, withTouch);
      }
    }
    if ( modifications.size() > 0 ) {
      accessControlManager.setAcl(Security.ZONE_CONTENT, path, modifications.toArray(new AclModification[modifications.size()]));
    }
  }
  protected Operation getOperation(String op) {
    op = op.toLowerCase();
    if ( op.equals("replace")) {
      return Operation.OP_REPLACE;
    } else if ( op.equals("and")) {
      return Operation.OP_AND;
    } else if ( op.equals("or")) {
      return Operation.OP_OR;
    } else if ( op.equals("xor")) {
      return Operation.OP_XOR;
    } else if ( op.equals("not")) {
      return Operation.OP_NOT;
    } else if ( op.equals("del")) {
      return Operation.OP_DEL;
    }
    return Operation.OP_REPLACE;
  }

  protected int getPermissionBitMap(JSONArray jsonArray) throws JSONException {
    int bitmap = 0;
    for ( int i = 0; i < jsonArray.length(); i++ ) {
      bitmap = bitmap | Permissions.parse(jsonArray.getString(i).toLowerCase()).getPermission();
    }
    return bitmap;
  }

  protected Class<?> getElementType(String key) {
    if ( key == null || key.length() == 0) {
      return Object.class;
    }
    String[] keyparts = StringUtils.split(key, "@",2);
    if ( keyparts.length < 2 ) {
      return Object.class;
    }
    Class<?> type = TYPES.get(keyparts[1]);
    if ( type == null ) {
      return Object.class;
    }
    return type;
  }

  protected String getPathElement(String key) {
    if ( key != null && key.length() > 0 && !"@".equals(key)) {
      if ( key.startsWith("@")) {
        return "";
      }
      return StringUtils.split(key, "@", 2)[0];
    }
    return null;
  }
  @SuppressWarnings("unchecked")
  protected <T> T[] getArray(JSONArray arr, Class<T> typeHint) throws JSONException {
    if ( arr.length() == 0 ) {
      if (typeHint.equals(Object.class)) {
        return (T[]) new String[0];
      } else {
        return (T[]) Array.newInstance(typeHint, 0);
      }
    }
    Object o = getObject(arr.get(0), typeHint);
    T[] array = (T[]) Array.newInstance(o.getClass(), arr.length());
    for ( int i = 0; i < arr.length(); i++ ) {
      array[i] = getObject(arr.get(i), typeHint);
    }
    return array;
  }
  @SuppressWarnings("unchecked")
  protected <T> T getObject(Object obj, Class<T> type) {
    if (JSONObject.NULL.equals(obj)) {
      return null;
    }

    if ( type.equals(Object.class)) {
      return (T) obj; // no type hint, just accept the json parser
    } else if ( type.equals(String.class)) {
      return (T) String.valueOf(obj);
    } else if ( type.equals(Integer.class) ) {
      if ( obj instanceof Integer ) {
        return (T) obj;
      } else {
        return (T) ((Integer)Integer.parseInt(String.valueOf(obj)));
      }
    } else if ( type.equals(Long.class)) {
      if ( obj instanceof Long) {
        return (T) obj;
      } else {
        return (T) ((Long)Long.parseLong(String.valueOf(obj)));
      }
    } else if ( type.equals(Double.class)) {
      if ( obj instanceof Double) {
        return (T) obj;
      } else {
        return (T) ((Double)Double.parseDouble(String.valueOf(obj)));
      }
    } else if ( type.equals(BigDecimal.class)) {
      if ( obj instanceof BigDecimal) {
        return (T) obj;
      } else {
        return (T) ( new BigDecimal(String.valueOf(obj)));
      }
    } else if ( type.equals(Calendar.class)) {
      if ( obj instanceof Calendar) {
        return (T) obj;
      } else {
        return (T) new ISO8601Date(String.valueOf(obj));
      }
    } else if ( type.equals(Boolean.class)) {
      if ( obj instanceof Boolean) {
        return (T) obj;
      } else {
        return (T) ((Boolean)Boolean.parseBoolean(String.valueOf(obj)));
      }
    }
    return null;
  }
}

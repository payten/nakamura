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

package org.sakaiproject.kernel.connections;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.kernel.api.connections.ConnectionException;
import org.sakaiproject.kernel.util.JcrUtils;
import org.sakaiproject.kernel.util.PathUtils;

/**
 * Simple utils which help us when working with connections
 * 
 * @author Aaron Zeckoski (azeckoski @ gmail.com)
 */
public class ConnectionUtils {

  /**
   * This will get a node (and optionally create it) with the given base path
   * and name)<br/>
   * (the full path is generated by taking the basepath and appending the 4-part
   * hash and then the name, example: /_user/contacts + /a0/b0/c0/d0 + /myName)<br/>
   * This will also assign the resourceType if the node is created
   * 
   * @param session
   *          the current JCR session
   * @param basePath
   *          the basePath (should start with /, can be "/" for root)
   * @param nodeName
   *          the name of the node to get (cannot be null or blank)
   * @param createIfNotExists
   *          if true then the nodes are created, otherwise an error is thrown
   *          is the node is not found
   * @param resourceType
   *          [OPTIONAL] if this is set and the node is created then this value
   *          will be set as the resource type
   * @return the Node at the given path
   * @throws ConnectionException
   *           if there is an error while trying to get the node
   */
  public static Node getStorageNode(Session session, String basePath,
      String nodeName, boolean createIfNotExists, String resourceType)
      throws ConnectionException {
    if (basePath == null || "".equals(basePath)) {
      throw new IllegalArgumentException("basePath cannot be null");
    }
    if (nodeName == null || "".equals(nodeName)) {
      throw new IllegalArgumentException("nodeName cannot be null");
    }
    Node n = null;
    String fullNodePath = basePath + PathUtils.getHashedPath(nodeName, 4);
    try {
      if (session.itemExists(fullNodePath)) {
        Item storeItem = session.getItem(fullNodePath);
        if (storeItem.isNode()) {
          n = (Node) storeItem;
        }
      } else {
        // create the node
        n = JcrUtils.deepGetOrCreateNode(session, fullNodePath);
        if (resourceType != null) {
          n.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, resourceType);
        }
      }
    } catch (RepositoryException e) {
      throw new ConnectionException(500, e.getMessage(), e);
    }
    return n;
  }

  /**
   * Gets the first node which matches a given type starting at the current
   * resource
   * 
   * @param store
   *          a resource within the store.
   * @param resourceType
   *          the type to stop at when a node is found
   * @return the first Node that matches the resourceType OR null if none found
   * @throws RepositoryException
   */
  public static Node getStoreNodeByType(Resource store, String resourceType)
      throws RepositoryException {
    if (store == null || resourceType == null) {
      throw new IllegalArgumentException("store and resourceType must be set");
    }
    Session session = store.getResourceResolver().adaptTo(Session.class);
    String path = store.getPath();
    Node node = JcrUtils.getFirstExistingNode(session, path);
    return findNodeByType(resourceType, node);
  }

  /**
   * Gets the first node which matches a given type starting at the current node
   * 
   * @param node
   *          a node within the store.
   * @param resourceType
   *          the type to stop at when a node is found
   * @return the first Node that matches the resourceType OR null if none found
   * @throws RepositoryException
   */
  public static Node getStoreNodeByType(Node node, String resourceType)
      throws RepositoryException {
    if (node == null || resourceType == null) {
      throw new IllegalArgumentException("store and resourceType must be set");
    }
    Session session = node.getSession();
    String path = node.getPath();
    Node n = JcrUtils.getFirstExistingNode(session, path);
    return findNodeByType(resourceType, n);
  }

  /**
   * @param resourceType
   *          the type to limit the search by
   * @param node
   *          the node to start searching from
   * @return the first Node that matches the resourceType OR null if none found
   * @throws RepositoryException
   */
  private static Node findNodeByType(String resourceType, Node node)
      throws RepositoryException {
    while (!"/".equals(node.getPath())) {
      if (node.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)
          && resourceType.equals(node.getProperty(
              JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString())) {
        return node;
      }
      node = node.getParent();
    }
    return null;
  }

}

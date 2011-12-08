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
package org.sakaiproject.nakamura.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.cluster.ClusterServer;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.api.search.DeletedPathsService;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.Query;

/**
 * Manage a cache of deleted paths as signaled by content deletion events. The cache is
 * cleared when an index commit event is received.
 * <p>
 * A cache is managed per machine but shared with the cluster. This is to cut down on
 * overwriting a centrally managed but unsynchronized cache. Each machine should have only
 * one version of this service actively managing that machines cache so it should always
 * work with the authoritative state.
 */
@Component
@Service
@Property(name = "event.topics", value = {
    "org/sakaiproject/nakamura/lite/content/DELETE",
    "org/sakaiproject/nakamura/solr/COMMIT",
    "org/sakaiproject/nakamura/solr/SOFT_COMMIT"
})
public class DeletedPathsServiceImpl implements EventHandler, DeletedPathsService {
  public static final String DELETED_PATH_CACHE = "deletedPathQueue";

  @Reference
  private CacheManagerService cacheManagerService;

  @Reference
  private ClusterTrackingService clusterTrackingService;

  public DeletedPathsServiceImpl() {
  }

  protected DeletedPathsServiceImpl(CacheManagerService cacheManagerService,
      ClusterTrackingService clusterTrackingService) {
    this.cacheManagerService = cacheManagerService;
    this.clusterTrackingService = clusterTrackingService;
  }

  /**
   * Get an instance of the cache used to track paths that have been marked as
   * deleted since the last Solr commit.  This cache is shared by all nodes in a
   * cluster, acting as a sort of shared memory.
   */
  private Cache<String> getDeletedPathCache() {
    return cacheManagerService.getCache(DELETED_PATH_CACHE, CacheScope.CLUSTERREPLICATED);
  }

  /**
   * Record a path as having been deleted, preventing it from appearing in search results.
   *
   * @param path the path that was deleted
   */
  private synchronized void storeDeletedPath(String path) {
    Cache<String> cache = getDeletedPathCache();
    String myId = clusterTrackingService.getCurrentServerId();

    // add the new path to the last position
    int pathCount = PropertiesUtil.toInteger(cache.get("pathCount@" + myId), 0);
    cache.put("path[" + pathCount + "]@" + myId,
        SearchUtil.escapeString(path, Query.SOLR));
    cache.put("pathCount@" + myId, String.valueOf(pathCount + 1));

    // clean out any paths that start with the path we've just added
    for (int idx = 0; idx < pathCount; idx++) {
      String key = "path[" + idx + "]@" + myId;
      String cachedPath = cache.get(key);
      // check for null in case a previous pass deleted the element. we don't do garbage
      // collection because the list is expected to be too short lived.
      if (cachedPath != null && !cachedPath.equals(path) && cachedPath.startsWith(path)) {
        cache.remove(key);
      }
    }
  }

  /**
   * Clear the list of deleted nodes for this node.
   */
  private synchronized void clearDeletedPaths() {
    Cache<String> cache = getDeletedPathCache();
    String myId = clusterTrackingService.getCurrentServerId();

    int pathCount = PropertiesUtil.toInteger(cache.get("pathCount@" + myId), 0);
    for (int idx = 0; idx < pathCount; idx++) {
      cache.remove("path[" + idx + "]@" + myId);
    }

    cache.put("pathCount@" + myId, Integer.toString(0));
  }

  // ---------- DeletedPathsService interface ----------------------------------
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.search.DeletedPathsService#getDeletedPaths()
   */
  @Override
  public List<String> getDeletedPaths() {
    List<String> deletedPaths = new ArrayList<String>();
    Cache<String> cache = getDeletedPathCache();

    for (ClusterServer server : clusterTrackingService.getAllServers()) {
      String serverId = server.getServerId();
      int pathCount = PropertiesUtil.toInteger(cache.get("pathCount@" + serverId), 0);

      for (int idx = 0; idx < pathCount; idx++) {
        String path = (String)cache.get("path[" + idx + "]@" + serverId);

        if (path != null) {
          deletedPaths.add(SearchUtil.escapeString(path, Query.SOLR));
        }
      }
    }

    return deletedPaths;
  }

  // ---------- EventHandler interface -----------------------------------------
  public void handleEvent(Event event) {
    String topic = event.getTopic();

    if ("org/sakaiproject/nakamura/lite/content/DELETE".equals(topic)) {
      String path = (String)event.getProperty("path");

      if (path != null) {
        storeDeletedPath(path);
      }
    } else if ("org/sakaiproject/nakamura/solr/COMMIT".equals(topic)
        || "org/sakaiproject/nakamura/solr/SOFT_COMMIT".equals(topic)) {
      clearDeletedPaths();
    }
  }
}

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

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.cluster.ClusterServer;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.memory.MapCacheImpl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class DeletedPathsServiceImplTest {

  @Mock
  private CacheManagerService cacheManagerService;
  @Mock
  private ClusterTrackingService clusterTrackingService;
  @Mock
  private ClusterServer clusterServer;

  private DeletedPathsServiceImpl service;

  @Before
  public void setUp() {
    // setup cluster server
    when(clusterServer.getServerId()).thenReturn(
        DeletedPathsServiceImplTest.class.getName());

    // setup cluster tracking service
    when(clusterTrackingService.getCurrentServerId()).thenReturn(
        DeletedPathsServiceImplTest.class.getName());
    when(clusterTrackingService.getAllServers()).thenReturn(
        Lists.newArrayList(clusterServer));

    // setup cache manager service
    Cache<Object> cache = new MapCacheImpl<Object>(
        DeletedPathsServiceImpl.DELETED_PATH_CACHE, CacheScope.CLUSTERREPLICATED);
    when(cacheManagerService.getCache(anyString(), any(CacheScope.class))).thenReturn(
        cache);

    service = new DeletedPathsServiceImpl(cacheManagerService, clusterTrackingService);
  }

  @Test
  public void coverageBooster() {
    new DeletedPathsServiceImpl();
  }

  @Test
  public void testAddPaths() throws Exception {
    // test for hard commits
    testAddPathsHelper("org/sakaiproject/nakamura/solr/COMMIT");
    // test for soft commits
    testAddPathsHelper("org/sakaiproject/nakamura/solr/SOFT_COMMIT");
  }

  private void testAddPathsHelper(String eventTopic) throws Exception {
    List<String> paths = Lists.newArrayList("/first/second", "/first/third",
        "/first/fourth");
    List<String> addedPaths = Lists.newArrayList();
    for (String path : paths) {
      addedPaths.add(path);
      service.handleEvent(new Event("org/sakaiproject/nakamura/lite/content/DELETE",
          ImmutableMap.of("path", path)));
      List<String> deletedPaths = service.getDeletedPaths();

      // make sure the lists are the same
      assertEquals(addedPaths, deletedPaths);
    }

    // clear out the paths
    service.handleEvent(new Event(eventTopic, ImmutableMap
        .of()));
    List<String> deletedPaths = service.getDeletedPaths();
    addedPaths.clear();
    assertEquals(addedPaths, deletedPaths);
  }

  @Test
  public void testDeleteParent() throws Exception {
    List<String> throwAwayPaths = Lists.newArrayList("/first/second", "/first/third",
        "/first/fourth");
    List<String> keeperPaths = Lists.newArrayList("/other", "/thing", "/first");
    List<String> addedPaths = Lists.newArrayList();
    // load up some paths
    for (String path : throwAwayPaths) {
      addedPaths.add(path);
      Event event = new Event("org/sakaiproject/nakamura/lite/content/DELETE",
          ImmutableMap.of("path", path));
      service.handleEvent(event);
    }

    // load some more paths but use /first to clear out what was added by throwAwayPaths
    for (String path : keeperPaths) {
      addedPaths.add(path);
      Event event = new Event("org/sakaiproject/nakamura/lite/content/DELETE",
          ImmutableMap.of("path", path));
      service.handleEvent(event);
    }

    assertEquals(keeperPaths, service.getDeletedPaths());
  }
}

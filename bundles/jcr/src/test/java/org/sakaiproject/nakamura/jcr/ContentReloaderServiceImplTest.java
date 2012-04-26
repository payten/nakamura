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
package org.sakaiproject.nakamura.jcr;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.jcr.ContentReloaderService.BUNDLE_CONTENT_NODE;
import static org.sakaiproject.nakamura.api.jcr.ContentReloaderService.PROP_CONTENT_LOADED;
import static org.sakaiproject.nakamura.jcr.ContentReloaderServiceImpl.CONTENT_LOADER_SERVICE_PID;

import java.util.List;
import java.util.NoSuchElementException;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.Component;
import org.apache.felix.scr.ScrService;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ContentReloaderServiceImplTest {
  @Mock
  ScrService scrService;
  @Mock
  SlingRepository slingRepo;
  @Mock
  Session jcrSession;
  @Mock
  Node bundleContent;
  @Mock
  NodeIterator bundleContentIter;
  @Mock
  Component contentLoader;

  ContentReloaderServiceImpl service;

  @Before
  public void setUp() throws Exception {
    when(bundleContent.getPath()).thenReturn(BUNDLE_CONTENT_NODE);
    when(bundleContent.getNodes()).thenReturn(bundleContentIter);

    when(jcrSession.nodeExists(BUNDLE_CONTENT_NODE)).thenReturn(true);
    when(jcrSession.getNode(BUNDLE_CONTENT_NODE)).thenReturn(bundleContent);

    when(slingRepo.loginAdministrative(null)).thenReturn(jcrSession);

    when(contentLoader.getState()).thenReturn(Component.STATE_DISABLED);
    when(scrService.getComponents(CONTENT_LOADER_SERVICE_PID)).thenReturn(
        new Component[] { contentLoader });

    service = new ContentReloaderServiceImpl();
    service.scrService = scrService;
    service.slingRepo = slingRepo;
  }

  @Test
  public void testListBundlesNoneFound() throws Exception {
    // set the return pattern to allow
    // 1. the default empty iterator to catch
    // 2. no bundle content node be found
    when(jcrSession.nodeExists(BUNDLE_CONTENT_NODE)).thenReturn(true).thenReturn(false);

    // check with an empty iterator
    String[] bundleNames = service.listLoadedBundles();
    assertNotNull(bundleNames);
    assertEquals(0, bundleNames.length);

    // check with no bundle content node
    bundleNames = service.listLoadedBundles();
    assertNotNull(bundleNames);
    assertEquals(0, bundleNames.length);
  }

  @Test
  public void testListBundles() throws Exception {
    mockBundleContentNodes();

    String[] bundleNames = service.listLoadedBundles();
    assertNotNull(bundleNames);
    assertEquals(2, bundleNames.length);
    assertEquals("testBundle0", bundleNames[0]);
    assertEquals("testBundle2", bundleNames[1]);
  }

  @Test
  public void testReloadAll() throws Exception {
    mockBundleContentNodes();
    when(jcrSession.hasPendingChanges()).thenReturn(true);

    List<String> bundleNames = service.reloadContent();
    assertEquals(2, bundleNames.size());
    assertEquals("testBundle0", bundleNames.get(0));
    assertEquals("testBundle2", bundleNames.get(1));

    verify(contentLoader).disable();
    verify(contentLoader).enable();
  }

  @Test
  public void testReloadSelect() throws Exception {
    mockBundleContentNodes();
    when(jcrSession.hasPendingChanges()).thenReturn(true).thenReturn(false);

    List<String> bundleNames = service.reloadContent("testBundle2");
    assertEquals(1, bundleNames.size());
    assertEquals("testBundle2", bundleNames.get(0));

    bundleNames = service.reloadContent("testBundle1");
    assertEquals(0, bundleNames.size());

    verify(contentLoader).disable();
    verify(contentLoader).enable();
  }

  @Test
  public void testReloadAllNoContentLoader() throws Exception {
    mockBundleContentNodes();
    when(jcrSession.hasPendingChanges()).thenReturn(true).thenReturn(false);
    when(scrService.getComponents(CONTENT_LOADER_SERVICE_PID)).thenReturn(null);

    List<String> bundleNames = service.reloadContent("testBundle2");
    assertEquals(1, bundleNames.size());
    assertEquals("testBundle2", bundleNames.get(0));

    bundleNames = service.reloadContent("testBundle1");
    assertEquals(0, bundleNames.size());

    verify(contentLoader, never()).disable();
    verify(contentLoader, never()).enable();
  }

  @Test
  public void testReloadRepositoryExceptionNoBundles() throws Exception {
    when(slingRepo.loginAdministrative(null)).thenThrow(new RepositoryException());

    List<String> bundleNames = service.reloadContent("testBundle1");
    assertNull(bundleNames);

    verify(contentLoader, never()).disable();
    verify(contentLoader, never()).enable();
  }

  @Test
  public void testReloadRepositoryExceptionOneBundle() throws Exception {
    mockBundleContentNodes();
    when(jcrSession.getNode(BUNDLE_CONTENT_NODE + "/" + "testBundle2")).thenThrow(new RepositoryException());
    
    List<String> bundleNames = service.reloadContent("testBundle0", "testBundle2");
    assertNull(bundleNames);

    verify(contentLoader, never()).disable();
    verify(contentLoader, never()).enable();
  }

  /**
   * Mock a node to contain enough for the service to treat as a bundle content node.
   * 
   * @param contentLoaded
   *          Value to assign content-loaded. If null, property isn't added.
   * @return
   */
  private Node mockBundleContentNode(String name, Boolean contentLoaded) throws Exception {
    Node node = mock(Node.class);
    when(node.getName()).thenReturn(name);
    when(node.getPath()).thenReturn(BUNDLE_CONTENT_NODE + "/" + name);

    if (contentLoaded != null) {
      when(node.hasProperty(PROP_CONTENT_LOADED)).thenReturn(true);

      Property property = mock(Property.class);
      when(property.getBoolean()).thenReturn(contentLoaded);
      when(node.getProperty(PROP_CONTENT_LOADED)).thenReturn(property);
    }
    return node;
  }

  private void mockBundleContentNodes() throws Exception {
    Node bundle0 = mockBundleContentNode("testBundle0", true);
    Node bundle1 = mockBundleContentNode("testBundle1", false);
    Node bundle2 = mockBundleContentNode("testBundle2", true);
    Node bundle3 = mockBundleContentNode("testBundle3", null);

    when(bundleContentIter.hasNext())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);

    when(bundleContentIter.nextNode())
        .thenReturn(bundle0)
        .thenReturn(bundle1)
        .thenReturn(bundle2)
        .thenReturn(bundle3)
        .thenThrow(new NoSuchElementException());

    when(jcrSession.nodeExists(bundle0.getPath())).thenReturn(true);
    when(jcrSession.nodeExists(bundle1.getPath())).thenReturn(true);
    when(jcrSession.nodeExists(bundle2.getPath())).thenReturn(true);
    when(jcrSession.nodeExists(bundle3.getPath())).thenReturn(true);

    when(jcrSession.getNode(bundle0.getPath())).thenReturn(bundle0);
    when(jcrSession.getNode(bundle1.getPath())).thenReturn(bundle1);
    when(jcrSession.getNode(bundle2.getPath())).thenReturn(bundle2);
    when(jcrSession.getNode(bundle3.getPath())).thenReturn(bundle3);
  }
}

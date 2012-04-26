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

import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.felix.scr.Component;
import org.apache.felix.scr.ScrService;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.jcr.ContentReloaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 *
 */
@org.apache.felix.scr.annotations.Component
@Service
public class ContentReloaderServiceImpl implements ContentReloaderService {
  public static final String CONTENT_LOADER_SERVICE_PID = "org.apache.sling.jcr.contentloader.internal.ContentLoaderService";

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ContentReloaderServiceImpl.class);

  @Reference
  protected ScrService scrService;

  @Reference
  protected SlingRepository slingRepo;

  public String[] listLoadedBundles() throws RepositoryException,
      PathNotFoundException {
    Session jcrSession = null;
    List<String> loadedBundles = Lists.newArrayList();
    try {
      // clear content-loaded from bundles
      jcrSession = slingRepo.loginAdministrative(null);
      if (jcrSession.nodeExists(BUNDLE_CONTENT_NODE)) {

        // get a list of bundles to
        Node bundleContentNode = jcrSession.getNode(BUNDLE_CONTENT_NODE);
        NodeIterator bundleNodes = bundleContentNode.getNodes();

        // remove the content-loaded property from bundle-content
        while (bundleNodes.hasNext()) {
          Node bundleNode = bundleNodes.nextNode();
          if (bundleNode.hasProperty(PROP_CONTENT_LOADED)
              && bundleNode.getProperty(PROP_CONTENT_LOADED).getBoolean()) {
            loadedBundles.add(bundleNode.getName());
          }
        }
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }

    Collections.sort(loadedBundles, String.CASE_INSENSITIVE_ORDER);
    return loadedBundles.toArray(new String[loadedBundles.size()]);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.jcr.ContentReloaderService#reload(java.lang.String[])
   */
  public List<String> reloadContent(String... bundleNames) {
    List<String> removed = null;
    try {
      removed = removeContentLoadedProp(bundleNames);
    } catch (RepositoryException e) {
      LOGGER.warn(e.getMessage(), e);
    }

    if (removed != null && removed.size() > 0) {
      restartContentLoaderService();
    }

    return removed;
  }

  /**
   * Remove the <code>content-loaded</code> property from /var/sling/bundle-content for
   * the bundles named. If no bundles are named, all known active bundles are used.
   * 
   * @param bundleNames
   * @throws RepositoryException
   * @throws PathNotFoundException
   */
  private List<String> removeContentLoadedProp(String... bundleNames)
      throws RepositoryException, PathNotFoundException {
    List<String> updatedBundles = Lists.newArrayList();
    Session jcrSession = null;
    try {
      // clear content-loaded from bundles
      jcrSession = slingRepo.loginAdministrative(null);

      // process the requested list
      if (bundleNames == null || bundleNames.length == 0) {
        bundleNames = this.listLoadedBundles();
      }
      for (String bundleName: bundleNames) {
        if (removeContentLoaded(jcrSession, bundleName)) {
          updatedBundles.add(bundleName);
        }
      }

      // save any changes
      if (jcrSession.hasPendingChanges()) {
        jcrSession.save();
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }

    return updatedBundles;
  }

  /**
   * @param updatedBundles
   * @param jcrSession
   * @param bundle
   * @throws RepositoryException
   */
  private boolean removeContentLoaded(Session jcrSession, String bundleName)
      throws RepositoryException {
    String bundlePath = BUNDLE_CONTENT_NODE + "/" + bundleName;
    if (jcrSession.nodeExists(bundlePath)) {
      Node bundleNode = jcrSession.getNode(bundlePath);
      if (bundleNode.hasProperty(PROP_CONTENT_LOADED)
          && bundleNode.getProperty(PROP_CONTENT_LOADED).getBoolean()) {
        bundleNode.getProperty(PROP_CONTENT_LOADED).remove();
        return true;
      }
    }
    return false;
  }

  /**
   * Restart the content loader service in Sling to cause it to reload initial content
   * from bundles.
   */
  private void restartContentLoaderService() {
    // disable then enable the content loader. this will cause it to scan bundles looking
    // for those that haven't loaded content yet.
    Component[] contentLoaders = scrService.getComponents(CONTENT_LOADER_SERVICE_PID);

    if (contentLoaders == null || contentLoaders.length == 0) {
      LOGGER.warn("Didn't find any content loaders to restart.");
      return;
    }

    for (Component contentLoader : contentLoaders) {
      contentLoader.disable();
      // count the tries and stop after 100 (100 * 100ms == 10s)
      int tries = 0;
      while (contentLoader.getState() != Component.STATE_DISABLED && tries < 100) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          break;
        }
        tries++;
      }
      contentLoader.enable();
    }
  }
}

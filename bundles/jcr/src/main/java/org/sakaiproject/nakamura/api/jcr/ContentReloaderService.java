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
package org.sakaiproject.nakamura.api.jcr;

import java.util.List;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

/**
 * Service to trigger reloading initial content found in bundles.
 */
public interface ContentReloaderService {
  /** Copied from org.apache.sling.jcr.contentloader.internal.ContentLoaderService */
  String BUNDLE_CONTENT_NODE = "/var/sling/bundle-content";
  String PROP_CONTENT_LOADED = "content-loaded";

  /**
   * List the bundles that are known to have had content loaded from them to JCR.
   *
   * @return
   * @throws RepositoryException
   * @throws PathNotFoundException
   */
  String[] listLoadedBundles() throws RepositoryException, PathNotFoundException;

  /**
   * Reload the content found in the named bundles.
   *
   * @param bundlesNames
   *          Names of bundles to reload the content of. If empty, reload all bundles
   *          found in the database.
   * @return {@link List} of bundle names that were updated to reload content.
   */
  List<String> reloadContent(String... bundleNames);
}

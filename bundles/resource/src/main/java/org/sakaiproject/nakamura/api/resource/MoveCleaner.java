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
package org.sakaiproject.nakamura.api.resource;

import java.util.List;

import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

/**
 * <p>
 * Clean up, reset or otherwise fix data after it has been moved.
 * </p>
 * <p>
 * This is often needed when content has a path reference to some other part of content
 * and after a move the path reference becomes invalid.
 * </p>
 * <p>
 * A move cleaner responds to the moving of content based. Move cleaners are intended to
 * respond to content with a resource type. The resource type is defined by setting the
 * component configuration property of,
 * </p>
 * 
 * <pre>
 * sakai.movecleaner.resourcetyp
 * </pre>
 * 
 * {@link MoveCleaner#RESOURCE_TYPE}
 * <p>
 * to match the resource type(s) the cleaner knows how to handle. This is a required
 * property and a warning is written to the log if the property is not found.
 * </p>
 */
public interface MoveCleaner {
  String RESOURCE_TYPE = "sakai.movecleaner.resourcetype";

  /**
   * Clean some content after it has been moved. This method is guaranteed to receive a
   * content object that has the property "sling:resourceType" per the definition of the
   * OSGi configuration property.
   *
   * @param fromPath
   *          The path the data was moved from.
   * @param toContent
   *          The content where the data now lives.
   * @param cm
   *          The content manager used to move the data.
   * @return A list of modifications made when cleaning the moved data.
   */
  List<Modification> clean(String fromPath, String toPath, ContentManager cm)
      throws StorageClientException, AccessDeniedException;
}

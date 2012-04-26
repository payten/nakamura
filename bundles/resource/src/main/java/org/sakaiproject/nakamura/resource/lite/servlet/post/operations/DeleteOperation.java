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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

public class DeleteOperation extends AbstractSparsePostOperation {

  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException, IOException {

    Iterator<Resource> res = getApplyToResources(request);
    if (res == null) {

      Resource resource = request.getResource();
      if (contentPath == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND, "Missing source contentPath for delete");
        return;
      }
      StorageClientUtils.deleteTree(contentManager, contentPath);
      changes.add(Modification.onDeleted(resource.getPath()));

    } else {

      while (res.hasNext()) {
        Resource resource = res.next();
        Content contentItem = resource.adaptTo(Content.class);
        if (contentItem != null) {
          StorageClientUtils.deleteTree(contentManager, contentItem.getPath());
          changes.add(Modification.onDeleted(resource.getPath()));
        }
      }

    }

  }
}

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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActionRecord;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.util.PathUtils;

@Component
@Service(value = SparsePostOperation.class)
@Reference(name = "moveCleaners",
    cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
    policy = ReferencePolicy.DYNAMIC,
    referenceInterface = MoveCleaner.class,
    bind = "bindCleaner", unbind = "unbindCleaner")
@Property(name = "sling.post.operation", value = "move")
public class MoveOperation extends AbstractSparsePostOperation {
  private static final String REPLACE_PAR = ":replace";
  private static final String KEEP_DEST_HISTORY_PAR = ":keepDestHistory";
  private static final String DEST_PAR = ":dest";

  private CopyOnWriteArrayList<MoveCleaner> moveCleaners;

  public MoveOperation() {
    moveCleaners = new CopyOnWriteArrayList<MoveCleaner>();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.servlets.HtmlResponse, org.sakaiproject.nakamura.api.lite.content.ContentManager, java.util.List, java.lang.String)
   */
  @Override
  public void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException {

    boolean replace = Boolean.parseBoolean(request.getParameter(REPLACE_PAR));
    boolean keepDestHistory = PropertiesUtil.toBoolean(
        request.getParameter(KEEP_DEST_HISTORY_PAR), true);
    String from = contentPath;
    String to = PathUtils.toUserContentPath(request.getParameter(DEST_PAR));
    List<ActionRecord> moves = contentManager.move(from, to, replace, keepDestHistory);
    for (int i = 0; i < moves.size(); i++) {
      ActionRecord move = moves.get(i);
      changes.add(Modification.onMoved(move.getFrom(), move.getTo()));
    }

    // if there are cleaners, see if one can handle this content
    if (!moveCleaners.isEmpty()) {
      // get the content from the new location and clean it before returning
      for (MoveCleaner cleaner : moveCleaners) {
        // consult each cleaner and collect the modifications
        List<Modification> mods = cleaner.clean(from, to, contentManager);
        if (mods != null) {
          changes.addAll(mods);
        }
      }
    }
  }

  // ---------- SCR integration ----------
  protected void bindCleaner(MoveCleaner cleaner) {
    moveCleaners.add(cleaner);
  }

  protected void unbindCleaner(MoveCleaner cleaner) {
    moveCleaners.remove(cleaner);
  }
}

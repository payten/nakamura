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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.util.PathUtils;

import com.google.common.collect.Lists;

/**
 * Move cleaner that handles pages in a Sakai Document
 */
@Component
@Service
public class SakaiDocPageMoveCleaner implements MoveCleaner {
  public static final String MIMETYPE = "_mimeType";
  public static final String SAKAI_DOC_MIMETYPE = "x-sakai/document";
  public static final String MESSAGESTORE_PROP = "sakai:messagestore";
  public static final String LTI_KEYS_NODE = "ltiKeys";

  @Reference
  protected Repository repository;

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.resource.MoveCleaner#clean(String, Content,
   *      ContentManager)
   */
  @Override
  public List<Modification> clean(String fromPath, String toPath, ContentManager cm)
      throws StorageClientException, AccessDeniedException {
    /*
     * There are checks for nodes that start with "id" because we can assume that pages
     * and widget container nodes will start that way. This keeps the processing from
     * hitting the database for a node that doesn't contain widget information.
     */

    // pages should start with an id. if this doesn't, leave now.
    String pageId = PathUtils.lastElement(toPath);
    if (!pageId.startsWith("id")) {
      return Collections.emptyList();
    }

    // look a level up to find the document that holds this page
    String sakaiDocPath = PathUtils.getParentReference(toPath);
    Content docContent = cm.get(sakaiDocPath);

    // make sure we're working with a sakai document
    if (!SAKAI_DOC_MIMETYPE.equals(docContent.getProperty(MIMETYPE))) {
        return Collections.emptyList();
    }

    // setup the var to return
    List<Modification> mods = Lists.newArrayList();

    // a page will have a 'rows' element. we want the widgets that are stored in the page.
    String rowsPath = toPath + "/rows";
    if (pageId.startsWith("id") && cm.exists(rowsPath)) {

      // looks like we found a page. look at the child nodes to find the widget
      // containers
      Iterator<String> widgetContainerPaths = cm.listChildPaths(toPath);
      while (widgetContainerPaths.hasNext()) {

        // check for a type subnode to clean the message store path
        String widgetContainerPath = widgetContainerPaths.next();
        String widgetContainerId = PathUtils.lastElement(widgetContainerPath);
        if (widgetContainerId.startsWith("id")) {

          if (cm.exists(widgetContainerPath + "/comments")) {
            mods.addAll(handleMessageStore(fromPath, toPath, widgetContainerPath
                + "/comments", cm));
          }
          if (cm.exists(widgetContainerPath + "/discussion")) {
            mods.addAll(handleMessageStore(fromPath, toPath, widgetContainerPath
                + "/discussion", cm));
          }
          if (cm.exists(widgetContainerPath + "/basiclti")) {
            mods.addAll(handleBasicLti(fromPath, toPath, widgetContainerPath
                + "/basiclti", cm));
          }
        }
      }
    }
    return mods;
  }

  /**
   * Clean the path of a message store to point to the new location of the widget.
   *
   * @param fromPath Where the widget was.
   * @param toPath Where the widget is now.
   * @param type The type of widget
   * @param widgetContainerPath
   * @param cm
   * @return
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private List<Modification> handleMessageStore(String fromPath, String toPath,
      String widgetPath, ContentManager cm) throws AccessDeniedException,
      StorageClientException {
    Content widget = cm.get(widgetPath);
    List<Modification> mods = Lists.newArrayList();

    // check for a name that starts with id* and a path to the message
    if (widget.hasProperty(MESSAGESTORE_PROP)) {
      String messagestore = String.valueOf(widget.getProperty(MESSAGESTORE_PROP));
      String newMessageStore = StringUtils.replace(messagestore, fromPath, toPath);
      if (!messagestore.equals(newMessageStore)) {
        widget.setProperty(MESSAGESTORE_PROP, newMessageStore);
        cm.update(widget);
        mods.add(Modification.onModified(toPath + "/" + MESSAGESTORE_PROP));
      }
    }
    return mods;
  }

  /**
   * Move the ltiKeys node to the new destination. Since this node is under lock and key,
   * we have to take special precautions when moving it.
   *
   * @param fromPath
   * @param toPath
   * @param widgetPath
   * @param cm
   * @return
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private List<Modification> handleBasicLti(String fromPath, String toPath,
      String widgetPath, ContentManager cm) throws AccessDeniedException,
      StorageClientException {
    List<Modification> mods = Lists.newArrayList();
    String fromWidgetPath = StringUtils.replace(widgetPath, toPath, fromPath);
    String fromKeysNode = fromWidgetPath + "/" + LTI_KEYS_NODE;
    Session adminSession = null;
    try {
      // get an admin session to check for the secure node
      adminSession = repository.loginAdministrative();
      ContentManager adminCM = adminSession.getContentManager();
      if (adminCM.exists(fromKeysNode)) {
        String toKeysNode = widgetPath + "/" + LTI_KEYS_NODE;
        adminCM.move(fromKeysNode, toKeysNode);
        mods.add(Modification.onMoved(fromKeysNode, toKeysNode));
      }
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }
    return mods;
  }
}

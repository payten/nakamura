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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.RemoveProperty;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.util.PathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This SakaiDocPagePublishOperation is a hack to overcome some issues with publishing draft
 * sakai doc pages. The reason this was created was because when a page is taken into edit mode,
 * all of its content, including comments and discussion posts (i.e., "sakai/message" resources)
 * are copied into a tmp_* node. When the page is saved/published, all those messages are copied
 * back, overwriting all of the existing messages.
 * 
 * The result is that if there are messages that were added while the page was being updated, they
 * are deleted.
 * 
 * This is not the ideal fix but it is the quickest and less risky at this point in releasing
 * 1.2.0. The ideal circumstances would be:
 * 
 * <ul>
 *  <li>The activity content for widgets would not be copied to the tmp_* space at all. This is not
 *  necessary when editing a page, and copying all messages may not scale when there are many:
 *  KERN-5447.</li>
 *  <li>We would have a "merge" operation that would allow for merging content in one tree into
 *  another, but there is no time to develop a new generic operation like this</li>
 * </ul>
 */
@Component
@Service(value = SparsePostOperation.class)
@Reference(name = "moveCleaners", cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
    policy = ReferencePolicy.DYNAMIC, referenceInterface = MoveCleaner.class, bind = "bindCleaner",
    unbind = "unbindCleaner")
@Property(name = "sling.post.operation", value = "publish-sakaidoc-page")
public class SakaiDocPagePublishOperation extends MoveOperation {
  
  private Pattern PATTERN_PAGE_PATH = Pattern.compile("^(.+/)tmp_(id[0-9]+)$");
  private Pattern PATTERN_PAGE_CHILDPATH = Pattern.compile("^(.+/)tmp_(id[0-9]+.*)$");
  
  @Override
  public void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException {
    Matcher tmpPageMatch = PATTERN_PAGE_PATH.matcher(contentPath);
    if (!tmpPageMatch.matches()) {
      throw new IllegalArgumentException(String.format(
          "Content path is not a draft Sakai Doc Page: '%s'", contentPath));
    }
    
    if (!contentManager.exists(contentPath)) {
      throw new IllegalArgumentException("Draft page does not exist!");
    }
    
    String targetPath = PathUtils.toUserContentPath(request.getParameter(":dest"));
    Content tmpPage = contentManager.get(contentPath);
    Content targetPage = contentManager.get(targetPath);
    
    /*
     * Do a full search through all messages in the tmp_id* storage to grab sakai/messages that have
     * changed. Any message that has changed will effectively overwrite and clobber any message in
     * the "live" page that has been updated. In this iteration, we only grab the path of that
     * content, to record the fact that it has changed for later.
     */
    final Set<String> messageUpdates = new HashSet<String>();
    depthFirstSearch(tmpPage, new ContentCallback() {
      @Override
      public void callback(Content content) {
         if (isMessage(content)) {
           Long created = (Long) content.getProperty(Content.CREATED_FIELD);
           Long updated = (Long) content.getProperty(Content.LASTMODIFIED_FIELD);
           if (!created.equals(updated)) {
             // the message was updated, so add its path.
             messageUpdates.add(getLivePath(content.getPath()));
           }
         }
      }
    });
    
    /*
     * Do a full search through all messages in the permanent (id*) storage to record the state
     * of those messages. When the move operations is performed later, all these messages will be
     * clobbered from storage. We want to put them back after to avoid losing edits, delets and
     * creations of new messages.
     */
    final Map<String, Content> messageStore = new HashMap<String, Content>();
    depthFirstSearch(targetPage, new ContentCallback() {
      @Override
      public void callback(Content content) {
        if (isMessage(content)) {
          // if the message was updated in the tmp_* storage, then the tmp_* version wins the
          // conflict. therefore we simply don't back-up the message here so it won't be pushed
          // back in later.
          if (!messageUpdates.contains(content.getPath())) {
            messageStore.put(content.getPath(), new Content(content.getPath(),
                new HashMap<String, Object>(content.getProperties())));
          }
        }
      }
    });
 
    // call the move operation
    super.doRun(request, response, contentManager, changes, contentPath);
    
    /*
     * Here we will push the backed-up messages back onto the live page. Any state of a message
     * that was changed since the page started editing (edit, create, delete) will be redone
     * by this block of code. The exception is that if a message was updated in the tmp_* space,
     * that will NOT be redone here, since that change wins the conflict.
     */
    for (Map.Entry<String, Content> entry : messageStore.entrySet()) {
      Content targetMessage = contentManager.get(entry.getKey());
      if (targetMessage != null) {
        // replace and clobber the message properties with that in the back-up ("source")
        Map<String, Object> newProps = new HashMap<String, Object>(entry.getValue().getProperties());
        // remove properties that don't exist in the source
        for (Map.Entry<String, Object> targetPropEntry : targetMessage.getProperties().entrySet()) {
          String targetKey = targetPropEntry.getKey();
          if (!newProps.containsKey(targetKey)) {
            // if it does not exist in the source, deleted it
            newProps.put(targetKey, new RemoveProperty());
          }
        }
        
        targetMessage = new Content(targetMessage.getPath(), newProps);
        
      } else {
        // the target message does not exist, we need to recreate it. this happens when the message
        // was created WHILE the page was being edited.
        targetMessage = entry.getValue();
      }
      
      contentManager.update(targetMessage);
    }
  }
  
  /**
   * Perform a depth-first search, hitting the {@code callback} when a new node is hit.
   * 
   * @param root
   * @param callback
   */
  private void depthFirstSearch(Content root, ContentCallback callback) {
    Iterable<Content> children = root.listChildren();
    if (children != null) {
      for (Content child : children) {
        depthFirstSearch(child, callback);
      }
    }
    callback.callback(root);
  }
  
  private String getLivePath(String path) {
    Matcher m = PATTERN_PAGE_CHILDPATH.matcher(path);
    if (m.matches()) {
      return String.format("%s%s", m.group(1), m.group(2));
    }
    return null;
  }
  
  private final boolean isMessage(Content content) {
    return "sakai/message".equals(content.getProperty(Content.SLING_RESOURCE_TYPE_FIELD));
  }
  
  /**
   * Simple callback interface for the depth-first search.
   */
  private interface ContentCallback {
    void callback(Content content);
  }
}

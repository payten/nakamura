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
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.api.files;

import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_COUNT;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.PathUtils;

import com.google.common.collect.Sets;

public class TagUtils {
  /**
   * Check if a node is a proper sakai tag.
   *
   * @param node
   *          The node to check if it is a tag.
   * @return true if the node is a tag, false if it is not.
   * @throws RepositoryException
   */
  public static boolean isTag(Content node) {
    if (node != null && node.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)
        && FilesConstants.RT_SAKAI_TAG.equals(node.getProperty(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY))) {
      return true;
    }
    return false;
  }

  public static boolean addTag(ContentManager contentManager, Content contentNode,
      Content tagNode)
      throws org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException,
      StorageClientException, RepositoryException {
    if (contentNode == null) {
      throw new RuntimeException(
          "Cant tag non existant nodes, sorry, both must exist prior to tagging. File:"
              + contentNode);
    }
    String tagName = String.valueOf(tagNode.getProperty(SAKAI_TAG_NAME));
    return addTag(contentManager, contentNode, StringUtils.defaultIfBlank(tagName, null));
  }

  private static boolean addTag(ContentManager contentManager, Content content, String tag)
      throws org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException,
      StorageClientException {
    boolean sendEvent = false;
    if (tag != null) {
      Map<String, Object> properties = content.getProperties();
      Set<String> nameSet = Sets.newHashSet(StorageClientUtils.nonNullStringArray((String[]) properties
          .get(SAKAI_TAGS)));
      if (!nameSet.contains(tag)) {
        nameSet.add(tag);
        content.setProperty(SAKAI_TAGS,
            nameSet.toArray(new String[nameSet.size()]));
        sendEvent = true;
      }

      if (sendEvent) {
        contentManager.update(content);
      }
    }
    return sendEvent;
  }

  public static boolean deleteTag(ContentManager contentManager, Content content,
      String tag)
      throws org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException,
      StorageClientException {

      if (StringUtils.isBlank(tag))
        return false;

    boolean updated = false;
    Map<String, Object> properties = content.getProperties();
    Set<String> nameSet = Sets.newHashSet(StorageClientUtils.nonNullStringArray((String[]) properties
        .get(SAKAI_TAGS)));
    if (nameSet.contains(tag)) {
      nameSet.remove(tag);
      content.setProperty(SAKAI_TAGS,
          nameSet.toArray(new String[nameSet.size()]));
      updated = true;
    }
    if (updated) {
      contentManager.update(content);
      return true;
    }
    return false;
  }

  public static Collection<String> ancestorTags(Content tagNode, ContentManager cm)
      throws AccessDeniedException, StorageClientException {
    Collection<String> rv = new ArrayList<String>();
    if (!isChildOfRoot(tagNode)) {
      String parentPath = PathUtils.getParentReference(tagNode.getPath());
      Content parentNode = cm.get(parentPath);
      if (TagUtils.isTag(parentNode)) {
        rv.add(String.valueOf(parentNode.getProperty(SAKAI_TAG_NAME)));
      }
      rv.addAll(ancestorTags(parentNode, cm));
    }
    return rv;
  }

  public static boolean isChildOfRoot(Content node) {
    String parentPath = PathUtils.getParentReference(node.getPath());
    return "".equals(parentPath) || "/".equals(parentPath);
  }

  public static boolean alreadyTaggedBelowThisLevel(Content tagNode, String[] tagNames, ContentManager cm) throws StorageClientException {
    List<String> tagNamesList = Arrays.asList(tagNames);
    Iterator<Content> childNodes = cm.listChildren(tagNode.getPath());
    while(childNodes.hasNext()){
      Content child = childNodes.next();
      if (alreadyTaggedBelowThisLevel(child, tagNames, cm)) {
        return true;
      }
      if (TagUtils.isTag(child)
          && tagNamesList.contains(String.valueOf(child.getProperty(SAKAI_TAG_NAME)))) {
        return true;
      }
    }
    return false;
  }
  
  public static boolean alreadyTaggedAtOrAboveThisLevel(String[] tagNames, List<String> peerTags) {
    for (String tagName : tagNames) {
      if (peerTags.contains(tagName)) {
        return true;
      }
    }
    return false;
  }

  public static void bumpTagCounts(Content nodeTag, String[] tagNames, boolean increase,
      boolean calledByAChild, ContentManager cm) throws StorageClientException,
      AccessDeniedException {
    if (calledByAChild || !TagUtils.alreadyTaggedBelowThisLevel(nodeTag, tagNames, cm)) {
      Long tagCount = increase ? 1L : 0L;
      if (nodeTag.hasProperty(SAKAI_TAG_COUNT)) {
        tagCount = StorageClientUtils.toLong(nodeTag.getProperty(SAKAI_TAG_COUNT));
        if (increase) {
          tagCount++;
        } else {
          tagCount--;
        }
      }
      nodeTag.setProperty(SAKAI_TAG_COUNT, tagCount);
      cm.update(nodeTag);
    }

     // if this node's parent is not the root, we keep going up
    if (!TagUtils.isChildOfRoot(nodeTag)) {
      List<String> peerTags = new ArrayList<String>();
      peerTags.addAll(TagUtils.ancestorTags(nodeTag, cm));
      String parentPath = PathUtils.getParentReference(nodeTag.getPath());
      Iterator<Content> peers = cm.listChildren(parentPath);
      while (peers.hasNext()) {
        Content peer = peers.next();
        if (TagUtils.isTag(peer) && !nodeTag.getPath().equals(peer.getPath())) {
          peerTags.add(String.valueOf(peer.getProperty(SAKAI_TAG_NAME)));
        }
      }
      if (!TagUtils.alreadyTaggedAtOrAboveThisLevel(tagNames, peerTags)) {
        Content parentNode = cm.get(parentPath);
        bumpTagCounts(parentNode, tagNames, increase, true, cm);
      }
    }
  }
}

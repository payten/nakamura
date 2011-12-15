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
package org.sakaiproject.nakamura.files.search;

import com.google.common.collect.Lists;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.exception.TikaException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.tika.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;

public class PageIndexingUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(PageIndexingUtil.class);

  public static void indexAllPages(Content content, ContentManager contentManager, SolrInputDocument doc, TikaService tikaService) throws PageIndexException {
    for (InputStream pageStream : getPageStreams(content, contentManager)) {
      try {
        doc.addField("content", tikaService.parseToString(pageStream));
      } catch (IOException e) {
        LOGGER.warn(e.getMessage());
      } catch (TikaException e) {
        LOGGER.warn(e.getMessage());
      }
    }
  }

  private static List<InputStream> getPageStreams(Content content, ContentManager contentManager) throws PageIndexException {
    List<InputStream> streams = Lists.newArrayList();
    for (Content page : getPages(content, contentManager)) {
      if (page.hasProperty("page")) {
        try {
          // The UX posts a string, but it may have been silently stored as a LongString value.
          String pageProperty = page.getProperty("page").toString();
          streams.add(new ByteArrayInputStream(pageProperty.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
          throw new PageIndexException("Could not get bytes from the page property because UTF-8 is an unsupported encoding.");
        }
      }
    }
    return streams;
  }

  private static List<Content> getPages(Content content, ContentManager contentManager) throws PageIndexException {
    List<Content> pages = Lists.newArrayList();
    try {
      for (String pagePath : getPagePaths(content)) {
        Content page = contentManager.get(pagePath);
        if (page != null) {
          pages.add(page);
        } else {
          LOGGER.warn("Unable to find page {} under {}", pagePath, content.getPath());
        }
      }
    } catch (StorageClientException e) {
      throw new PageIndexException("Unable to get the child page paths for " + content.getPath(), e);
    } catch (AccessDeniedException e) {
      throw new PageIndexException("Access denied getting the child page paths for " + content.getPath());
    }
    return pages;
  }

  private static Iterable<? extends String> getPagePaths(Content content) throws PageIndexException {
    List<String> pagePaths = Lists.newArrayList();
    JSONObject pageStructure = getPageStructure(content);
    for (String pageReference : getPageReferences(pageStructure)) {
      pagePaths.add(content.getPath() + "/" + pageReference);
    }
    return pagePaths;
  }

  private static Iterable<? extends String> getPageReferences(JSONObject pageStructure) throws PageIndexException {
    List<String> pageReferences = Lists.newArrayList();
    try {
      for (Iterator<String> iter = pageStructure.keys(); iter.hasNext(); ) {
        String pageStructureKey = iter.next();
        Object pageDescriptor = pageStructure.get(pageStructureKey);
        if (pageDescriptor instanceof JSONObject) {
          pageReferences.add(((JSONObject) pageDescriptor).getString("_ref"));
        }
      }
    } catch (JSONException e) {
      throw new PageIndexException("Failed to get page references for document page structure.", e);
    }
    return pageReferences;
  }

  private static JSONObject getPageStructure(Content content) throws PageIndexException {
    try {
      return new JSONObject(content.getProperty("structure0").toString());
    } catch (JSONException e) {
      throw new PageIndexException("Could not create JSON object for the pages of document " + content.getPath(), e);
    }
  }

  public static class PageIndexException extends Exception {
    public PageIndexException(String s) {
      super(s);
    }

    public PageIndexException(String s, Throwable t) {
      super(s, t);
    }
  }
}

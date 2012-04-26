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
package org.sakaiproject.nakamura.files.pool;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceProvider;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.jackrabbit.JackrabbitSparseUtils;
import org.sakaiproject.nakamura.api.resource.lite.SparseContentResource;
import org.sakaiproject.nakamura.cp.HasItem;
import org.sakaiproject.nakamura.cp.Item;
import org.sakaiproject.nakamura.cp.Manifest;
import org.sakaiproject.nakamura.cp.Metadata;
import org.sakaiproject.nakamura.cp.Organization;
import org.sakaiproject.nakamura.cp.Organizations;
import org.sakaiproject.nakamura.cp.Resources;
import org.sakaiproject.nakamura.lom.basic.General;
import org.sakaiproject.nakamura.lom.basic.LOMRoot;
import org.sakaiproject.nakamura.lom.elements.Description;
import org.sakaiproject.nakamura.lom.elements.Keyword;
import org.sakaiproject.nakamura.lom.elements.LangString;
import org.sakaiproject.nakamura.lom.elements.Title;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

@Component
@Service
@Property(name = ResourceProvider.ROOTS, value = {"/imscp" })
public class ExportIMSCP implements ResourceProvider {
  private static final String PROP_ARRAY_FMT = "/__array__%s__";

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportIMSCP.class);
  public static final String CONTENT_RESOURCE_PROVIDER = ExportIMSCP.class
      .getName();
  
  @Reference
  protected transient Repository repository;
  
  public Resource getResource(ResourceResolver resourceResolver,
      HttpServletRequest request, String path) {
    LOGGER.debug("Got Resource URI [{}]  Path [{}] ", request.getRequestURI(), path);
    return getResource(resourceResolver, path);
  }
  
  public Resource getResource(ResourceResolver resourceResolver, String path) {
    if (path.length() <= 7 || path.indexOf("/imscp/") != 0) {
      return null;
    }
    return resolveMappedResource(resourceResolver, path);
  }
  
  private Resource resolveMappedResource(ResourceResolver resourceResolver, String path) {
    String poolId = null;
    SparseContentResource cpr = null;
    Session session = null;
    File zipFile = null;
    ContentManager contentManager = null;
    Content content = null;
    try {
      session = repository.loginAdministrative();
      contentManager = session.getContentManager();
      
      if (path.startsWith("/imscp/")) {
        poolId = path.substring("/imscp/".length());
      }
      if (poolId != null && poolId.length() > 0) {
        if (poolId.indexOf('/') > 0) {
          poolId = poolId.substring(0, poolId.indexOf('/'));
        }
        
        content = contentManager.get(poolId);
        if ( content != null ) {
          String mimeType = (String)content.getProperty(Content.MIMETYPE_FIELD);
          if ("x-sakai/document".equals(mimeType)) {
            JSONObject structure = new JSONObject((String)content.getProperty("structure0"));
            Manifest manifest = getManifest(structure, content, contentManager);
            zipFile = getZipFile(manifest, content, poolId, contentManager);
            InputStream input = new FileInputStream(zipFile.getAbsolutePath());
            String filename = (String)content.getProperty(FilesConstants.POOLED_CONTENT_FILENAME) + ".zip";
            contentManager.writeBody(poolId + "/" + filename, input);
            content = contentManager.get(poolId + "/" + filename);
            content.setProperty(Content.MIMETYPE_FIELD, "application/zip");
            contentManager.update(content);
            Session userSession = JackrabbitSparseUtils.getSparseSession(resourceResolver
                .adaptTo(javax.jcr.Session.class));
            
            cpr = new SparseContentResource(content, userSession,
                resourceResolver, "/p/" + poolId + "/" + filename);
            cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
            
            LOGGER.debug("Resolved {} as {} ", path, cpr);
            input.close();
          }
        }
      }
    } catch (RepositoryException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      e.printStackTrace();
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage());
      LOGGER.debug(e.getMessage(), e);
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage());
      LOGGER.debug(e.getMessage(), e);
    } catch (IOException e) {
      LOGGER.warn(e.getMessage());
      LOGGER.debug(e.getMessage(), e);
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.warn(e.getMessage());
      LOGGER.debug(e.getMessage(), e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn("Failed to close admin session ",e);
        }
      }
      if (zipFile!= null && zipFile.exists()) {
        FileUtils.deleteQuietly(zipFile);
      }
    }
    return cpr;
  }
  
  private Manifest getManifest(JSONObject structure, Content content, ContentManager cm)
      throws JSONException, Exception {
    Manifest manifest = new Manifest();
    manifest.setOrganizations(new Organizations());
    Organization org = new Organization();
    Resources resources = new Resources();
    getItem (structure, org, content, resources, cm);
    manifest.getOrganizations().addOrganization(org);
    manifest.setResources(resources);
    Metadata metaData = new Metadata();
    metaData.setSchema("IMS Content");
    metaData.setSchemaVersion("1.2");
    
    LOMRoot lom = new LOMRoot();
    General general = new General();
    String desString = (String)content.getProperty(FilesConstants.SAKAI_DESCRIPTION);
    if (desString != null && desString.length() != 0) {
      Description description = new Description();
      LangString desLang = new LangString();
      desLang.setString(desString);
      description.setLangString(desLang);
      general.addDescription(description);
    }
    String[] keywords = (String[])content.getProperty(FilesConstants.SAKAI_TAGS);
    if (keywords != null && keywords.length != 0) {
      for (int i = 0; i < keywords.length; i++) {
        Keyword key = new Keyword();
        LangString keyLang = new LangString();
        keyLang.setString(keywords[i]);
        key.setLangString(keyLang);
        general.addKeyword(key);
      }
    }
    String filename = (String)content.getProperty(FilesConstants.POOLED_CONTENT_FILENAME);
    if (filename != null && filename.length() > 0) {
      Title title = new Title();
      LangString titleLang = new LangString();
      titleLang.setString(filename);
      title.setLangString(titleLang);
      general.setTitle(title);
    }
    lom.setGeneral(general);
    metaData.setLom(lom);
    manifest.setMetadata(metaData);
    return manifest;
  }
  
  private void getItem(JSONObject structure, HasItem item, Content content, Resources resources, ContentManager cm) throws JSONException {
    Iterator<String> keys = structure.keys();
    while (keys.hasNext()) {
      // make sure there's a key to work with
      String key = keys.next();
      if (key.length() == 0 || key.charAt(0) == '_') {
        continue;
      }

      // make sure there's a title to work with
      JSONObject itemJson = structure.getJSONObject(key);
      String title = itemJson.optString("_title");
      if (StringUtils.isBlank(title)) {
        continue;
      }

      Item newItem = new Item();
      newItem.setIdentifier(key);
      newItem.setTitle(title);
      
      String ref = itemJson.optString("_ref");
      if (StringUtils.isNotEmpty(ref)) {
        newItem.setIdentifierRef(ref);
        if (cm.exists(content.getPath() + "/" + ref)) {
            org.sakaiproject.nakamura.cp.Resource resource = new org.sakaiproject.nakamura.cp.Resource();
            resource.setIdentifier(ref);
            resources.addResource(resource);
            resource.setHref("resources/" + ref + ".html");
            item.addItem(newItem);
        }
      } else {
        item.addItem(newItem);
      }
    }
  }
  
  private File getZipFile(Manifest manifest, Content content, String poolId, ContentManager contentManager) 
      throws JSONException, IOException, StorageClientException, AccessDeniedException {
    String resourcesDir = "resources/";
    String filename = (String)content.getProperty(FilesConstants.POOLED_CONTENT_FILENAME);
    filename = filename.replaceAll("/", "_");
    File f = File.createTempFile(filename, ".zip");
    f.deleteOnExit();
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f));
    List<org.sakaiproject.nakamura.cp.Resource> resources = manifest.getResources().getResources();
    if (resources == null) {
      return null;
    }
    for (org.sakaiproject.nakamura.cp.Resource resource : resources) {
      Item item = new Item();
      Queue<Item> items = new LinkedList<Item>();
      items.addAll(manifest.getOrganizations().getOrganizations().get(0).getItems());
      while (!items.isEmpty()) {
        Item i = items.poll();
        if (i.getIdentifierRef() != null && i.getIdentifierRef().equals(resource.getIdentifier())) {
          item = i;
          break;
        }
        if (i.hasSubItems()) {
          items.addAll(i.getItems());
        }
      }
      String title = resource.getIdentifier() + ".html";
      String originTitle = title;
      if (item.getTitle() != null && item.getTitle().length() != 0) {
        originTitle = item.getTitle() + ".html";
      } 
      
      String page = collectPageContent(content, resource.getIdentifier(), contentManager);
      page = handlePage(page, contentManager, poolId, zos);
      page = "<html><head><title>" + originTitle + "</title></head><body>" + page + "</body></html>";
      InputStream input = new ByteArrayInputStream(page.getBytes());
      ZipEntry zae = new ZipEntry(resourcesDir + title);
      zos.putNextEntry(zae);
      IOUtils.copy(input, zos);
    }
    String xml = manifest.generateXML();
    InputStream input = new ByteArrayInputStream(xml.getBytes());
    ZipEntry zae = new ZipEntry("imsmanifest.xml");
    zos.putNextEntry(zae);
    IOUtils.copy(input, zos);
    zos.close();
    return f;
  }

  /**
   * Starting with <code>content</code>, walk the children deterministically in a
   * row-by-column format to find the elements of the pages. The content of the
   * <code>pagetitle</code> and <code>htmlblock</code> elements are collected to build up
   * the html of the page.
   *
   * @param content The content we need to find page content for.
   * @param resourceId Identifier of the resource being worked on.
   * @param cm Content manager to look up children.
   * @return
   */
  private String collectPageContent(Content content, String resourceId, ContentManager cm)
      throws AccessDeniedException, StorageClientException {
    StringBuilder page = new StringBuilder();

    // start at the resource node below the content
    String resPath = content.getPath() + "/" + resourceId;

    // loop through rows
    Content rows = cm.get(resPath + "/rows");
    for (int rowCount = 0; rows != null; rowCount++) {
      Content row = cm.get(rows.getPath() + String.format(PROP_ARRAY_FMT, rowCount));
      if (row != null) {

        // loop through the columns
        Content columns = cm.get(row.getPath() + "/columns");
        for (int columnCount = 0; columns != null; columnCount++) {
          Content column = cm.get(columns.getPath() + String.format(PROP_ARRAY_FMT, columnCount));
          if (column != null) {

            // loop through the elements.
            Content elements = cm.get(column.getPath() + "/elements");
            for (int elementCount = 0; elements != null; elementCount++) {
              Content element = cm.get(elements.getPath() + String.format(PROP_ARRAY_FMT, elementCount));
              if (element != null) {

                // we only deal with `pagetitle` and `htmlblock` because that's what
                // Nico said we should do.
                String type = String.valueOf(element.getProperty("type"));
                if ("pagetitle".equals(type) || "htmlblock".equals(type)) {
                  String id = String.valueOf(element.getProperty("id"));
                  String elementPath = resPath + "/" + id + "/" + type;
                  Content elementContent = cm.get(elementPath);
                  if (elementContent != null) {
                    // we've finally hit gold!
                    String contentText = String.valueOf(elementContent.getProperty("content"));
                    page.append(contentText);
                  }
                }
              } else {
                // break out of elements
                break;
              }
            }
          } else {
            // break out of columns
            break;
          }
        }
      } else {
        // break out of rows
        break;
      }
    }
    return page.toString();
  }

  private String handlePage(String page, ContentManager contentManager, String poolId, ZipOutputStream zos) 
      throws StorageClientException, AccessDeniedException, IOException {
    int index = 0; 
    if (StringUtils.isBlank(page)) {
      return "";
    }
    while ((index = page.indexOf("<img id=\"widget_embedcontent_id", index)) >= 0) {
      String embedHtml = page.substring(index, page.indexOf('>', index) + 1);
      index = index + "<img id=\"widget_embedcontent_".length();
      String embedId = page.substring(index, page.indexOf("\"", index));
      Content c = contentManager.get(poolId + "/" + embedId + "/embedcontent/items");
      if (c == null) {
        continue;
      }
      String resourcePath = (String)c.getProperty("__array__0__");
      if (resourcePath == null || resourcePath.length() == 0) {
        continue;
      }
      if (resourcePath.indexOf("/p/") >= 0) {
        resourcePath = resourcePath.substring(resourcePath.indexOf("/p/") + 3);
      }
      Content content = contentManager.get(resourcePath);
      if (content == null) {
        continue;
      }
      String mimeType = (String)content.getProperty(Content.MIMETYPE_FIELD);
      if (mimeType == null || mimeType.length() == 0) {
        continue;
      }
      if ("x-sakai/document".equals(mimeType)) {
        continue;
      }
      String fileName = (String)content.getProperty(FilesConstants.POOLED_CONTENT_FILENAME);
      String newHtml = "";
      if (mimeType.contains("image")) {
        newHtml = "<img src=\"" + fileName + "\" />";
      }
      else {
        newHtml = "<a href=\"" + fileName + "\">" + fileName + "</a>";
      }
      InputStream input = contentManager.getInputStream(resourcePath);
      ZipEntry zae = new ZipEntry("resources/" + fileName);
      zos.putNextEntry(zae);
      IOUtils.copy(input, zos);
      page = page.replace(embedHtml, newHtml);
    }
    return page;
  }
  
  public Iterator<Resource> listChildren(Resource parent) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("List Children [{}] ", parent.getPath());
    }
    return null;
  }
}

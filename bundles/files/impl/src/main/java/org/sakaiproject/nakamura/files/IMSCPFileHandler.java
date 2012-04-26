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

package org.sakaiproject.nakamura.files;

import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_FILENAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_DESCRIPTION;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.files.FileUploadHandler;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.cp.File;
import org.sakaiproject.nakamura.cp.HasItem;
import org.sakaiproject.nakamura.cp.HasItem.ITEMTYPE;
import org.sakaiproject.nakamura.cp.Item;
import org.sakaiproject.nakamura.cp.Manifest;
import org.sakaiproject.nakamura.cp.ManifestErrorException;
import org.sakaiproject.nakamura.cp.Organization;
import org.sakaiproject.nakamura.cp.Resource;
import org.sakaiproject.nakamura.lom.basic.General;
import org.sakaiproject.nakamura.lom.elements.Keyword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.activation.MimetypesFileTypeMap;

@Component(metatype = true)
@Service
public class IMSCPFileHandler implements FileUploadHandler {
  @Reference
  protected Repository sparseRepository;
  
  private static final Logger LOGGER = LoggerFactory
      .getLogger(IMSCPFileHandler.class);

  private static final String[] DEFAULT_ZIP_TYPES = {"application/zip", "application/x-zip", "application/x-zip-compressed", "application/x-compress", "application/x-compressed" };

  @Property( value = {"application/zip", "application/x-zip-compressed" } )
  private static final String ZIP_TYPES_PROP = "zip-types";

  private Set<String> zipTypes = ImmutableSet.copyOf(DEFAULT_ZIP_TYPES);
  
  private MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();

  @Property(boolValue = false)
  private static final String IS_HIERARCHICAL_PROP = "isHierarchical";
  
  private boolean isHierarchical = false;
  
  @Activate
  @Modified
  public void activate(Map<String, Object> properties ) {
      zipTypes = ImmutableSet.copyOf(PropertiesUtil.toStringArray(properties.get(ZIP_TYPES_PROP), DEFAULT_ZIP_TYPES)); 
      isHierarchical = PropertiesUtil.toBoolean(properties.get(IS_HIERARCHICAL_PROP), false);
  }
  
  public void handleFile(Map<String, Object> results, String poolId,
      InputStream fileInputStream, String userId, boolean isNew) throws IOException {
    try {
      Session adminSession = sparseRepository.loginAdministrative();
      ContentManager contentManager = adminSession.getContentManager();
      String type = (String)contentManager.get(poolId).getProperty(Content.MIMETYPE_FIELD);
      if (!zipTypes.contains(type)) {
        LOGGER.debug("PoolID {} has wrong mimetype, {} ignoring ",poolId, type);
        return;
      }
      
      // Check for Manifest file
      ZipInputStream zin = new ZipInputStream(fileInputStream);
      ZipEntry zipEntry;
      String manifestFilename = "imsmanifest.xml";
      boolean manifestFlag = false;
      while ((zipEntry = zin.getNextEntry()) != null) {
        if (manifestFilename.equalsIgnoreCase(zipEntry.getName())){
          manifestFlag = true;
          break;
        }
      }
      if (!manifestFlag) {
        LOGGER.debug("PoolID {} is not an IMSCP file, hence ignoring",poolId);
        zin.closeEntry();
        zin.close();
        return;
      }
      
      InputStream inputStream = contentManager.getInputStream(poolId);
      String name = (String)contentManager.get(poolId).getProperty(POOLED_CONTENT_FILENAME);
      Content content = createCourse(poolId, adminSession, inputStream, name, userId);
      if (content == null) {
        LOGGER.debug("PoolID {} is not IMS_CP format, ignore", poolId);
        return;
      }
      LOGGER.debug("Created IMS_CP {} ",content);
      results.put(name, ImmutableMap.of("poolId", (Object)poolId, "item", content.getProperties(), "type", "imscp"));
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (ManifestErrorException e) {
      LOGGER.warn(e.getMessage(), e);
    }
  }
  
  private Content createCourse(String poolId, Session session, InputStream value, String name, String userId) throws IOException, AccessDeniedException,
      StorageClientException, JSONException, ManifestErrorException {
    ContentManager contentManager = session.getContentManager();
    
    final ZipInputStream zin = new ZipInputStream(value);
    ZipEntry entry;
    String baseDir = poolId;
    String filename = "imsmanifest.xml";
    HashMap<String, String> fileContent = new HashMap<String, String>();
    List<String> filePaths = new ArrayList<String>();
    Manifest manifest = new Manifest();
    while ((entry = zin.getNextEntry()) != null) {
      filePaths.add(entry.getName());
      String entryType = mimeTypesMap.getContentType(entry.getName());
      if (entryType != null && entryType.contains("text")) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(getInputStream(zin)));
        StringBuilder builder = new StringBuilder();
        char[] chars = new char[4096];
        int length = 0;
        while (0 < (length = reader.read(chars))) {
          builder.append(chars, 0, length);
        }
        fileContent.put(entry.getName(), builder.toString());
        LOGGER.debug(" Saving Text file {} ",baseDir+"/"+entry.getName());
        contentManager.writeBody(baseDir + "/" + entry.getName(), new ByteArrayInputStream(builder.toString().getBytes()));
        reader.close();
        continue;
      }
      
      if (filename.equalsIgnoreCase(entry.getName())) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(getInputStream(zin)));
        StringBuilder builder = new StringBuilder();
        char[] chars = new char[4096];
        int length = 0;
        while (0 < (length = reader.read(chars))) {
          builder.append(chars, 0, length);
        }
        String xmlContent = builder.toString();
        // Abandon the last character, otherwise there will be parse error in toJSONObject method
        xmlContent = xmlContent.substring(0, xmlContent.lastIndexOf('>') + 1);
        manifest = new Manifest(xmlContent);
        LOGGER.debug(" Saving Manifest file {} ",baseDir+"/"+filename);
        contentManager.writeBody(baseDir + "/" + filename, new ByteArrayInputStream(builder.toString().getBytes()));
        reader.close();
        continue;
      }
      
      LOGGER.debug(" Saving file {} ",baseDir+"/"+entry.getName());
      contentManager.writeBody(baseDir + "/" + entry.getName(), getInputStream(zin));
    }
    zin.closeEntry();
    zin.close();

    contentManager.writeBody(poolId + "/" + name, contentManager.getInputStream(poolId));
    //Replace relative file path to JCR path
    for (Entry<String, String> fileContentEntry : fileContent.entrySet()) {
      String htmlContent = fileContentEntry.getValue();
      String key = fileContentEntry.getKey();
      String prefix = "";
      if (key.lastIndexOf('/') > 0) {
        prefix = key.substring(0, key.lastIndexOf('/') + 1);
      }
      if (filePaths != null && filePaths.size() > 0) {
        for (String s : filePaths) {
          if (s.length() <= prefix.length() || s.indexOf(prefix) < 0) {
            continue;
          }
          s = s.substring(prefix.length());
          htmlContent = htmlContent.replaceAll("\"" + s + "\"", "\"" + "p/" + poolId + "/" + prefix + s + "\"");
        }
      }
      fileContent.put(key, htmlContent);
    }      
    
    Content content = contentManager.get(poolId);
    content.setProperty(Content.MIMETYPE_FIELD, "x-sakai/document");
    content.setProperty("zipname", name);
    
    LOGGER.debug("Creating Sakai DOC from IMS-CP at {} ",poolId);
    String isHier = (String)content.getProperty("isHierarchical");
    
    JSONObject pageSetJSON;
    if (isHier != null && (isHier.equals("1") || isHier.equals("true"))) {
      pageSetJSON = manifestToPageSet(manifest, poolId, fileContent, true);
    } else if (isHier != null) {
      pageSetJSON = manifestToPageSet(manifest, poolId, fileContent, false);
    } else {
      pageSetJSON = manifestToPageSet(manifest, poolId, fileContent, isHierarchical);
    }
    
    Iterator<String> keys = pageSetJSON.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      content.setProperty(key, pageSetJSON.optString(key));
    }

    contentManager.update(content);
    return contentManager.get(poolId);
  }

  /**
   * Get Inputstream from ZipInputStream
   * @param zin
   * @return
   */
  private InputStream getInputStream(final ZipInputStream zin) {
    return new InputStream() {
      public int read() {
        try {
          return zin.read();
        } catch (IOException e) {
          LOGGER.warn(e.getMessage(), e);
          return -1;
        }
      }
    };
  }

  /**
   * Turn manifest structure to Sakai doc structure
   * @param manifest
   * @param poolId
   * @param fileContent
   * @return
   * @throws JSONException
   */
  private JSONObject manifestToPageSet(Manifest manifest, String poolId, 
      HashMap<String, String> fileContent, boolean isHierarchical) throws JSONException {
    JSONObject pages = new JSONObject();
    List<Organization> orgs = manifest.getOrganizations().getOrganizations();
    String description = "";
    
    StringBuffer keywords = new StringBuffer();
    String courseName = "";
    if (manifest.getMetadata() != null) {
      if (manifest.getMetadata().getLom() != null) {
        if (manifest.getMetadata().getLom().getGeneral() != null ) {
          General general = manifest.getMetadata().getLom().getGeneral();
          if (general.getDescription() != null && general.getDescription().size() != 0) {
            description = general.getDescription().get(0).getLangString().getString();
          }
          if (general.getKeyword() != null && general.getKeyword().size() != 0) {
            List<Keyword> keys = manifest.getMetadata().getLom().getGeneral().getKeyword();
            for (int i = 0; i < keys.size(); i++) {
              if (i > 0) keywords.append(",");
              keywords.append(keys.get(i).getLangString().getString());
            }
          }
          if (general.getTitle() != null) {
            courseName = general.getTitle().getLangString().getString();
          }
        }
      }
    }
    pages.put(SAKAI_DESCRIPTION, description);
    if (!"".equals(courseName)) {
      pages.put(POOLED_CONTENT_FILENAME, courseName);
    }
    if (keywords.length() != 0) {
      pages.put(SAKAI_TAGS, keywords.toString());
    }
    
    JSONArray allResources = new JSONArray();
    HashMap<String, JSONObject> resourceJSON = new HashMap<String, JSONObject> ();
    if (fileContent != null) {
      Set<String> keys = fileContent.keySet();
      int i = 0;
      for (String key : keys) {
        for (Resource res : manifest.getResources().getResources()) {
          if (res.getHref().equals(key)) {
            JSONObject resJSON = resourceToJson(res, poolId, i++, fileContent);
            resourceJSON.put(res.getIdentifier(), resJSON);
            allResources.put(resJSON);
          }
        }
      }
    }
    pages.put("resources", allResources);
    
    JSONObject structureJSON = new JSONObject();
    if (orgs != null && orgs.size() != 0) {
      int orgIndex = 0;
      for (int i = 0; i < orgs.size(); i++) {
        if (!orgs.get(i).hasSubItems()) {
          continue;
        }
        List<HasItem> items = new ArrayList<HasItem>();
        if (isHierarchical) {
          items.add(orgs.get(i));
        } else {
          List<Item> li = getLeafItems(orgs.get(i));
          items.addAll(li);
        }
        for (int j = 0; j < items.size(); j++) {
          if (isHierarchical && (items.get(j).getTitle() == null || items.get(j).getTitle().length() == 0)) {
            items.addAll(items.get(j).getItems());
            continue;
          }
          JSONObject object = itemToJson(items.get(j), poolId, orgIndex++, manifest,
              resourceJSON, "id" + String.valueOf(orgIndex), isHierarchical);
          structureJSON.put(object.getString("_id"), object);
        }
      }
    }
    pages.put("structure0", structureJSON);
    return pages;
  }
  
  private JSONObject itemToJson (HasItem item, String poolId, int index, Manifest manifest, 
      HashMap<String, JSONObject> resourceJSON, String itemId, boolean isHierarchical) throws JSONException{
    JSONObject itemJSON = new JSONObject();
    itemJSON.put("_id", itemId);
    itemJSON.put("_title", item.getTitle());
    itemJSON.put("_order", index);
    itemJSON.put("_canEdit", true);
    itemJSON.put("_canSubedit", true);
    itemJSON.put("_nonEditable", false);
    itemJSON.put("_ref", "");
    int subIndex = 0;
    JSONArray elementsArray = new JSONArray();
    if (item.getType() == HasItem.ITEMTYPE.ITEM) {
      Item i = (Item)item;
      if (i.getIdentifierRef() != null) {
        itemJSON.put("_ref", resourceJSON.get(i.getIdentifierRef()).get("_id"));
        if (isHierarchical && i.hasSubItems()){
          JSONObject object = new JSONObject(itemJSON, new String[]{"_title", "_canEdit", "_canSubedit", "_nonEditable", "_ref"});
          object.put("_id", "sub" + item.getIdentifier());
          object.put("_order", 0);
          JSONObject mainObject = new JSONObject(object,
              new String[] {"_title", "_ref", "_canEdit", "_canSubedit", "_nonEditable", "_poolpath"});
          mainObject.put("_id", "_main");
          mainObject.put("_elements", new JSONArray());
          object.put("main", mainObject);
          itemJSON.put(object.getString("_id"), object);
          
          elementsArray.put(object);
          subIndex = 1;
        }
      }
    }
    
    if (isHierarchical && item.hasSubItems()) {
      for (int i = 0; i < item.getItems().size(); i++) {
        Item subItem = item.getItems().get(i);
        JSONObject subJSON = itemToJson(subItem, poolId, subIndex++, manifest, resourceJSON, 
            itemId + "id" + String.valueOf(i), isHierarchical);
        itemJSON.put(subJSON.getString("_id"), subJSON);
        elementsArray.put(subJSON);
      }
    }
    JSONObject mainObject = new JSONObject(itemJSON,
        new String[] {"_title", "_ref", "_canEdit", "_canSubedit", "_nonEditable", "_poolpath"});
    mainObject.put("_id", "_main");
    mainObject.put("_elements", new JSONArray());
    itemJSON.put("main", mainObject);
    itemJSON.put("_elements", elementsArray);
    return itemJSON;
  }
  
  private JSONObject resourceToJson (Resource res, String poolId, int index, 
      HashMap<String, String> fileContent) throws JSONException {
    JSONObject resourceJSON = new JSONObject();
    //       String resID = StorageClientUtils.insecureHash(res.getHref());
    String resID = "id" + String.valueOf(2000000 + index);
    resourceJSON.put("_id", resID);
    resourceJSON.put("_path", poolId + "/" + res.getHref());       
    String contentType = mimeTypesMap.getContentType(res.getHref());
    if (contentType == null) {
      contentType = "application/octet-stream";
    }
    resourceJSON.put("_mimeType", contentType);
    JSONArray fileArray = new JSONArray();
    if (res.getFiles() != null) {
      for (int k = 0; k < res.getFiles().size(); k++) {
        File f = res.getFiles().get(k);
        fileArray.put(k, poolId + "/" + f.getHref());
      }
      resourceJSON.put("_dependencyPaths", fileArray);
    }
    resourceJSON.put("page", fileContent.get(res.getHref()));
    return resourceJSON;
  }
  
  private List<Item> getLeafItems(HasItem org) {
    List<Item> result = new ArrayList<Item>();
    if (org.getType() == ITEMTYPE.ITEM ) {
      Item item = (Item)org;
      if (item.getIdentifierRef() != null && item.getIdentifierRef().trim().length() > 0) {
        result.add(item);
      }
    }
    if (org.hasSubItems()) {
      for (int i = 0; i < org.getItems().size(); i++) {
        result.addAll(getLeafItems(org.getItems().get(i)));
      }
    }
    return result;
  }
}

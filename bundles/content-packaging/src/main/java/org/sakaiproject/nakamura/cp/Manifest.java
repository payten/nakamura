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

package org.sakaiproject.nakamura.cp;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.xml.XML;
import org.sakaiproject.nakamura.lom.type.JSONUtil;

import java.util.ArrayList;
import java.util.List;

public class Manifest {
  private Organizations organizations;
  private Resources resources;
  private List<Manifest> subManifests;
  private String identifier;
  private String version;
  private String xmlBase;
  private Metadata metadata;
  
  private JSONObject json;

  public Manifest() {
    super();
  }
  
  public Manifest(JSONObject json) throws ManifestErrorException {
    this.setJSON(json, "manifest");
    this.init();
  }
  
  public Manifest(String xmlcontent) throws ManifestErrorException, JSONException { 
    this(XML.toJSONObject(xmlcontent));
  }

  protected void init() throws ManifestErrorException{
    String organizationsName = "organizations";
    String resourcesName = "resources";
    String manifestName = "manifest";
    String identifierName = "identifier";
    String versionName = "version";
    String xmlBaseName = "base";
    String metadataName = "metadata";
    String lomName = "lom";
    
    JSONObject orgJSON = JSONUtil.getJSONObject(json, organizationsName);
    if (orgJSON != null) {
      organizations = new Organizations(orgJSON);
    } else {
      throw new ManifestErrorException("There is not Organizations element in manifest.");
    }
    
    JSONObject resourcesJSON = JSONUtil.getJSONObject(json, resourcesName);
    if (resourcesJSON != null) {
      resources = new Resources(resourcesJSON);
    } else {
      throw new ManifestErrorException("There is not Resources element in manifest");
    }
    
    JSONObject metaJSON = JSONUtil.getJSONObject(json, metadataName);
    if (metaJSON !=null) {
      metadata = new Metadata(metaJSON);
    } else {
      JSONObject object = JSONUtil.getJSONObject(json, "manifestMetadata");
      if (object != null) {
        metadata = new Metadata(object);
      } else if (JSONUtil.getJSONObject(json, lomName) != null) {
        metadata = new Metadata(json);
      }
    }
    
    JSONObject subManifestJSON = JSONUtil.getJSONObject(json, manifestName);
    if (subManifestJSON == null) {
      JSONArray subArray = JSONUtil.getJSONArray(json, manifestName);
      if (subArray != null) {
        for (int i = 0; i < subArray.length(); i++) {
          JSONObject object = subArray.optJSONObject(i);
          if (object != null) {
            try {
              JSONObject m = new JSONObject();
              m.put(manifestName, object);
              addSubManifest(new Manifest(m));
            } catch (JSONException e) {
              
            }
          }
        }
      }
    } else {
      try {
        JSONObject m = new JSONObject();
        m.put(manifestName, subManifestJSON);
        addSubManifest(new Manifest(m));
      } catch (JSONException e) {
        
      }
    }
    identifier = JSONUtil.getStringValue(json, identifierName);
    xmlBase = JSONUtil.getStringValue(json, xmlBaseName);
    version = JSONUtil.getStringValue(json, versionName);
  }  
  
  private void setJSON(JSONObject json, String manifestName) throws ManifestErrorException{
    JSONObject j = JSONUtil.getJSONObject(json, manifestName);
    if (j == null) {
      throw new ManifestErrorException("Manifest element is not found");
    }
    this.json = j;
  }
  public Organizations getOrganizations() {
    return organizations;
  }

  public void setOrganizations(Organizations organizations) {
    this.organizations = organizations;
  }

  public Resources getResources() {
    return resources;
  }

  public void setResources(Resources resources) {
    this.resources = resources;
  }

  public List<Manifest> getSubManifests() {
    return subManifests;
  }
  
  public void addSubManifest(Manifest m) {
    if (subManifests == null) {
      subManifests = new ArrayList<Manifest>();
    }
    subManifests.add(m);
  }

  public void setSubManifests(List<Manifest> subManifests) {
    this.subManifests = subManifests;
  }

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getXmlBase() {
    return xmlBase;
  }

  public void setXmlBase(String xmlBase) {
    this.xmlBase = xmlBase;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }
  
  public String generateXML() {
    StringBuilder head = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<manifest");
    String attr = " xmlns=\"http://www.imsglobal.org/xsd/imscp_v1p1\" " +
    		"xmlns:imsmd=\"http://www.imsglobal.org/xsd/imsmd_v1p2\" " +
    		"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
    		"xsi:schemaLocation=\"http://www.imsglobal.org/xsd/imscp_v1p1 " +
    		"http://www.imsglobal.org/xsd/imscp_v1p1.xsd " +
    		"http://www.imsglobal.org/xsd/imsmd_v1p2 " +
    		"http://www.imsglobal.org/xsd/imsmd_v1p2.xsd\"";
    head.append(attr);
    StringBuilder sb = new StringBuilder("");
    if (this.getMetadata() != null) {
      sb.append(this.getMetadata().generateXML());
    }
    if (this.getIdentifier() != null) {
      head.append(" identifier=\"" + this.getIdentifier() + "\"");
    }
    if (this.getVersion() != null) {
      head.append(" version=\"" + this.getVersion() + "\"");
    }
    if (this.getXmlBase() != null) {
      head.append(" xml:base=\"" + this.getXmlBase() + "\"");
    }
    if (this.getOrganizations() != null) {
      sb.append(this.getOrganizations().generateXML());
    }
    if (this.getResources() != null) {
      sb.append(this.getResources().generateXML());
    }
    if (this.getSubManifests() != null) {
      for (int i = 0; i < this.getSubManifests().size(); i++) {
        sb.append(this.getSubManifests().get(i).generateXML());
      }
    }
    return new String(head.toString() + ">" + sb.toString() + "</manifest>");
  }
}

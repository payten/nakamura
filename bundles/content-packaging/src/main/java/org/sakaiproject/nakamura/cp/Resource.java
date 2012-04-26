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
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;

import java.util.ArrayList;
import java.util.List;

public class Resource extends HasMetadata {
  private List<File> files;
  private List<Dependency> dependencies;
  private String type;
  private String xmlBase;
  private String href;

  public Resource() {
    super();
  }
  
  public Resource(JSONObject json) {
    this.json = json;
    super.init();
    this.init();
  }

  public void init() {
    String fileName = "file";
    String dependencyName = "dependency";
    String typeName = "type";
    String xmlBaseName = "base";
    String hrefName = "href";
    JSONObject fileJSON = JSONUtil.getJSONObject(json, fileName);
    if (fileJSON == null) {
      JSONArray fileArray = JSONUtil.getJSONArray(json, fileName);
      if (fileArray != null) {
        for (int i = 0; i < fileArray.length(); i++) {
          JSONObject object = fileArray.optJSONObject(i);
          if (object != null) {
            addFile(new File(object));
          }
        }
      }
    } else {
      addFile(new File(fileJSON));
    }
    
    JSONObject dependencyJSON = JSONUtil.getJSONObject(json, dependencyName);
    if (dependencyJSON == null) {
      JSONArray dependencyArray = JSONUtil.getJSONArray(json, dependencyName);
      if (dependencyArray != null) {
        for (int i = 0; i < dependencyArray.length(); i++) {
          JSONObject object = dependencyArray.optJSONObject(i);
          if (object != null) {
            addDependency(new Dependency(object));
          }
        }
      }
    } else {
      addDependency(new Dependency(dependencyJSON));
    }
    
    type = JSONUtil.getStringValue(json, typeName);
    xmlBase = JSONUtil.getStringValue(json, xmlBaseName);
    href = JSONUtil.getStringValue(json,hrefName);
  }

  public List<File> getFiles() {
    return files;
  }
  
  public void addFile(File f) {
    if (files == null) {
      files = new ArrayList<File>();
    }
    files.add(f);
  }
  
  public void addFile(File f, int index) {
    if (files == null) {
      files = new ArrayList<File> ();
    }
    files.add(index, f);
  }
  
  public void setFiles(List<File> files) {
    this.files = files;
  }
  
  public List<Dependency> getDependencies() {
    return dependencies;
  }
  
  public void addDependency(Dependency d) {
    if (dependencies == null) {
      dependencies = new ArrayList<Dependency>();
    }
    dependencies.add(d);
  }
  
  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
  }
  
  public String getType() {
    return type;
  }
  
  public void setType(String type) {
    this.type = type;
  }
  
  public String getXmlBase() {
    return xmlBase;
  }
  
  public void setXmlBase(String xmlBase) {
    this.xmlBase = xmlBase;
  }
  
  public String getHref() {
    return href;
  }
  
  public void setHref(String href) {
    this.href = href;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder(super.generateXML());
    StringBuilder head = new StringBuilder("<resource");
    if (this.getIdentifier() != null) {
      head.append(" identifier=\"" + this.getIdentifier() + "\"");
    }
    if (this.getType() != null) {
      head.append(" type=\"" + this.getType() + "\"");
    }
    if (this.getXmlBase() != null) {
      head.append(" xml:base=\"" + this.getXmlBase() + "\"");
    }
    if (this.getHref() != null) {
      head.append(" href=\"" + this.getHref() + "\"");
    }
    if (this.getFiles() != null) {
      for (int i = 0; i < this.getFiles().size(); i++) {
        sb.append(this.getFiles().get(i).generateXML());
      }
    }
    if (this.getDependencies() != null) {
      for (int i = 0; i < this.getDependencies().size(); i++) {
        sb.append(this.getDependencies().get(i).generateXML());
      }
    }
    if (sb.toString().equals("") && head.equals("<resource")) {
      return sb.toString();
    }
    return new String(head.toString() + ">" + sb.toString() + "</resource>");
  }
}

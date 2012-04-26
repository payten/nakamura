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
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class Resources extends Serialize {
  
  private List<Resource> resources;
  private String xmlBase;

  public Resources() {
    super();
  }
  
  public Resources(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String resourceName = "resource";
    String xmlBaseName = "base";
    JSONObject resourceJSON = JSONUtil.getJSONObject(json, resourceName);
    if (resourceJSON == null) {
      JSONArray resourceArray = JSONUtil.getJSONArray(json, resourceName);
      if (resourceArray != null) {
        for (int i = 0; i < resourceArray.length(); i++) {
          JSONObject object = resourceArray.optJSONObject(i);
          if (object != null) {
            addResource(new Resource(object));
          }
        }
      }
    } else {
      addResource(new Resource(resourceJSON));
    }
    
    xmlBase = JSONUtil.getStringValue(json, xmlBaseName);
  }

  public List<Resource> getResources() {
    return resources;
  }

  public void addResource(Resource r) {
    if (resources == null) {
      resources = new ArrayList<Resource>();
    }
    resources.add(r);
  }
  
  public void addResource(Resource r, int index) {
    if (resources == null) {
      resources = new ArrayList<Resource>();
    }
    resources.add(index, r);
  }
  
  public Resource searchResource (String id) {
    if (resources == null || id == null || "".equals(id)) {
      return null;
    }
    for (Resource r : resources) {
      if (id.equalsIgnoreCase(r.getIdentifier())) {
        return r;
      }
    }
    return null;
  }
  
  public void setResources(List<Resource> resources) {
    this.resources = resources;
  }

  public String getXmlBase() {
    return xmlBase;
  }

  public void setXmlBase(String xmlBase) {
    this.xmlBase = xmlBase;
  }
  
  @Override
  public String generateXML() {
    StringBuilder head = new StringBuilder("<resources");
    StringBuilder sb = new StringBuilder("");
    if (this.getXmlBase() != null) {
      head.append(" xml:base=\"" + this.getXmlBase() + "\"");
    }
    if (this.getResources() != null) {
      for (int i = 0; i < this.getResources().size(); i++){
        sb.append(this.getResources().get(i).generateXML());
      }
    }

    return new String(head.toString() + ">" + sb.toString() + "</resources>");
  }
}

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

public class Organizations extends Serialize {
  private String defaultID;
  private List<Organization> organizations;

  public Organizations() {
    super();
  }
  
  public Organizations(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String defaultName = "default";
    String organizationName = "organization";
    defaultID = JSONUtil.getStringValue(json, defaultName);
    
    JSONObject orgJSON = JSONUtil.getJSONObject(json, organizationName);
    if (orgJSON == null) {
      JSONArray orgArray = JSONUtil.getJSONArray(json, organizationName);
      if (orgArray != null) {
        for (int i = 0; i < orgArray.length(); i++) {
          JSONObject object = orgArray.optJSONObject(i);
          if (object != null) {
            addOrganization(new Organization(object));
          }
        }
      }
    } else {
      addOrganization(new Organization(orgJSON));
    }
    
  }

  public String getDefaultID() {
    return defaultID;
  }

  public void setDefaultID(String defaultID) {
    this.defaultID = defaultID;
  }

  public List<Organization> getOrganizations() {
    return organizations;
  }
  
  public void addOrganization(Organization o) {
    if (organizations == null) {
      organizations = new ArrayList<Organization>();
    }
    organizations.add(o);
  }
  
  public void addOrganization(Organization o, int index) {
    if (organizations == null) {
      organizations = new ArrayList<Organization>();
    }
    organizations.add(index, o);
  }
  
  public Organization getOrganization(String id) {
    if (organizations == null || id == null || "".equals(id)) {
      return null;
    }
    for (Organization o : organizations) {
      if (id.equals(o.getIdentifier())) {
        return o;
      }
    }
    return null;
  }
  
  public Item searchItem(String id) {
    if (organizations == null || id == null || "".equals(id)) {
      return null;
    }
    for (Organization o : organizations) {
      Item i = o.searchSubItem(id);
      if (i != null) {
        return i;
      }
    }
    return null;
  }

  public void setOrganizations(List<Organization> organizations) {
    this.organizations = organizations;
  }
  
  @Override
  public String generateXML() {
    StringBuilder head = new StringBuilder("<organizations");
    StringBuilder sb = new StringBuilder("");
    if (this.getDefaultID() != null) {
      head.append(" default=\"" + this.getDefaultID() + "\"");
    }
    if (this.getOrganizations() != null) {
      for (int i = 0; i < this.getOrganizations().size(); i++){
        sb.append(this.getOrganizations().get(i).generateXML());
      }
    }
    
    return new String(head.toString() + ">" + sb.toString() + "</organizations>");
  }
}

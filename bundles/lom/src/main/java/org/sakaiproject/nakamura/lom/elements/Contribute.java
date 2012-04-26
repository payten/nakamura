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

package org.sakaiproject.nakamura.lom.elements;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class Contribute extends Serialize{

  private Role role;
  private Date date; 
  private List<Entity> entity;
  
  public static enum CONTRIBUTETYPE {LIFECYCLE, METAMETADATA};
  private CONTRIBUTETYPE type;
  
  public Contribute (CONTRIBUTETYPE contributeType) {
    super();
    type = contributeType;
  }
  
  public Contribute (JSONObject json, CONTRIBUTETYPE contributeType) {
    super(json);
    type = contributeType;
  }

  @Override
  protected void init() {
    String roleName = "role";
    String entityName = "entity";
    String dateName = "date";
    JSONObject roleJSON = JSONUtil.getJSONObject(json, roleName);
    if (roleJSON != null) {
      role = new Role(roleJSON, type);
    }
    
    String entityString = JSONUtil.getStringValue(json, entityName);
    if (entityString == null) {
      JSONArray entitysArray = JSONUtil.getJSONArray(json, entityName);
      if (entitysArray != null) {
        for (int i = 0; i < entitysArray.length(); i++) {
          String s = entitysArray.optString(i);
          if (s != null) {
            addEntity(s);
          }
        }
      }
    } else {
      addEntity(entityString);
    }
    
    JSONObject dateJSON = JSONUtil.getJSONObject(json, dateName);
    if (dateJSON != null) {
      date = new Date(dateJSON);
    }
  }
  
  public CONTRIBUTETYPE getType() {
    return type;
  }
  
  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public List<Entity> getEntity() {
    return entity;
  }

  public void addEntity (String vcard) {
    if (entity == null) {
      entity = new ArrayList<Entity>();
    }
    entity.add(new Entity(vcard));
  }
  public void setEntity(List<Entity> entity) {
    this.entity = entity;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getDate() != null) {
      sb.append(this.getDate().generateXML());
    }
    if (this.getEntity() != null) {
      for (int i = 0; i < this.getEntity().size(); i++) {
        sb.append(entity.get(i).generateXML());
      }
    }
    if (this.getRole() != null) {
      sb.append(this.getRole().generateXML());
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String ("<contribute>" + sb.toString() + "</contribute>");
  }
}

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

package org.sakaiproject.nakamura.lom.basic;

import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.elements.Date;
import org.sakaiproject.nakamura.lom.elements.Description;
import org.sakaiproject.nakamura.lom.elements.Entity;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

public class Annotation extends Serialize{

  private Entity entity;
  private Date date;
  private Description description;
  
  public Annotation() {
    super();
  }
  
  public Annotation(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String entityName = "entity";
    String dateName = "date";
    String descriptionName = "description";
    String entityString = JSONUtil.getStringValue(json, entityName);
    setEntity(entityString);
    JSONObject dateJSON = JSONUtil.getJSONObject(json, dateName);
    if (dateJSON != null) {
      date = new Date(dateJSON);
    }
    
    JSONObject desJSON = JSONUtil.getJSONObject(json, descriptionName);
    if (desJSON != null) {
      description = new Description(desJSON);
    }
  }

  public Entity getEntity() {
    return entity;
  }

  public void setEntity(String vcard) {
    if (vcard == null || vcard.length() == 0) {
      this.entity = null;
      return;
    }
    Entity e = new Entity(vcard);
    this.entity = e;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public Description getDescription() {
    return description;
  }

  public void setDescription(Description description) {
    this.description = description;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getEntity() != null) {
      sb.append("<entity>" + this.getEntity() + "</entity>");
    }
    if (this.getDescription() != null) {
      sb.append(this.getDescription().generateXML());
    }
    if (this.getDate() != null) {
      sb.append(this.getDate().generateXML());
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<annotation>" + sb.toString() + "</annotation>");
  }
}

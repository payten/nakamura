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

public class Requirement extends Serialize {

  private List<OrComposite> orComposite; 
  
  public Requirement() {
    super();
  }
  
  public Requirement(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String orCompositeName = "orComposite";
    JSONObject orJSON = JSONUtil.getJSONObject(json, orCompositeName);
    if (orJSON == null) {
      JSONArray orArray = JSONUtil.getJSONArray(json, orCompositeName);
      if (orArray != null) {
        for (int i = 0; i < orArray.length(); i++){
          JSONObject object = orArray.optJSONObject(i);
          if (object != null) {
            addOrComposite(new OrComposite(object));
          }
        }
      }
    } else {
      addOrComposite (new OrComposite(orJSON));
    }

  }
  
  public List<OrComposite> getOrComposite() {
    return orComposite;
  }

  public void addOrComposite(OrComposite or) {
    if (orComposite == null) {
      orComposite = new ArrayList<OrComposite>();
    }
    orComposite.add(or);
  }
  
  public void setOrComposite(List<OrComposite> orComposite) {
    this.orComposite = orComposite;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getOrComposite() != null) {
      for (int i = 0; i < this.getOrComposite().size(); i++) {
        sb.append(this.getOrComposite().get(i).generateXML());
      }
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<requirement>" + sb.toString() + "</requirement>");
  }

}

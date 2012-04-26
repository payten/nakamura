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

import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

public class Taxon extends Serialize {

  private String id;
  private Entry entry;

  public Taxon() {
    super();
  }
  
  public Taxon(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String idName = "id";
    String entryName = "entry";
    
    id = json.optString(idName);
    
    JSONObject entryJSON = JSONUtil.getJSONObject(json, entryName);
    if (entryJSON != null)
      entry = new Entry(entryJSON);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Entry getEntry() {
    return entry;
  }

  public void setEntry(Entry entry) {
    this.entry = entry;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getId() != null) {
      sb.append("<id>" + this.getId() + "</id>");
    }
    if (this.getEntry() != null) {
      sb.append(this.getEntry().generateXML());
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<taxon>" + sb.toString() + "</taxon>");
  }
}

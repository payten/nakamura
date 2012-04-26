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

import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;

public class Organization extends HasItem {
  private String structure;
  
  public Organization() {
    super();
    this.type = HasItem.ITEMTYPE.ORGANIZATION;
  }
  
  public Organization(JSONObject json) {
    this.type = HasItem.ITEMTYPE.ORGANIZATION;
    this.json = json;
    super.init();
    this.init();
  }

  public void init() {
    String structureName = "structure";
    structure = JSONUtil.getStringValue(json, structureName);
  }

  public String getStructure() {
    return structure;
  }
  
  public void setStructure(String structure) {
    this.structure = structure;
  }
  
  @Override
  public String generateXML() {
    StringBuilder head = new StringBuilder("<organization");
    StringBuilder sb = new StringBuilder(super.generateXML());
    if (this.getIdentifier() != null) {
      head.append(" identifier=\"" + this.getIdentifier() + "\"");
    }
    if (this.getStructure() != null) {
      head.append(" structure=\"" + this.getStructure() + "\"");
    }
    if (sb.toString().equals("") && head.toString().equals("<organization")) {
      return sb.toString();
    }
    return new String(head + ">" + sb.toString() + "</organization>");
  }
}

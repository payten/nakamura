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

public class Item extends HasItem {
  private String identifierRef;
  private String isvisible;
  private String parameters;

  public Item() {
    super();
    this.type = HasItem.ITEMTYPE.ITEM;
  }
  
  public Item(JSONObject json) {
    this.type = HasItem.ITEMTYPE.ITEM;
    this.json = json;
    super.init();
    this.init();    
  }

  @Override
  protected void init() {
    String identifierRefName = "identifierRef";
    String isvisibleName = "isvisible";
    String parametersName = "parameters";
    identifierRef = JSONUtil.getStringValue(json, identifierRefName);
    isvisible = JSONUtil.getStringValue(json, isvisibleName);
    parameters = JSONUtil.getStringValue(json, parametersName);
  }
  
  public String getIdentifierRef() {
    return identifierRef;
  }

  public void setIdentifierRef(String identifierRef) {
    this.identifierRef = identifierRef;
  }

  public String getIsvisible() {
    return isvisible;
  }

  public void setIsvisible(String isvisible) {
    this.isvisible = isvisible;
  }

  public String getParameters() {
    return parameters;
  }

  public void setParameters(String parameters) {
    this.parameters = parameters;
  }
  
  @Override
  public String generateXML() {
    StringBuilder head = new StringBuilder("<item");
    StringBuilder sb = new StringBuilder(super.generateXML());
    if (this.getIdentifier() != null) {
      head.append(" identifier=\"" + this.getIdentifier() + "\"");
    }
    if (this.getIdentifierRef() != null) {
      head.append(" identifierref=\"" + this.getIdentifierRef() + "\"");
    }
    if (this.getIsvisible() != null) {
      head.append(" isvisible=\"" + this.getIsvisible() + "\"");
    }
    if (this.getParameters() != null) {
      head.append(" parameters=\"" + this.getParameters() + "\"");
    }
    if (sb.toString().equals("") && head.toString().equals("<item")) {
      return sb.toString();
    }
    return new String(head + ">" + sb.toString() + "</item>");
  }
}

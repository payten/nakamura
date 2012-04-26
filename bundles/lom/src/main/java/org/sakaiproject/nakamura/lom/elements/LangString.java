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

public class LangString extends Serialize {
  
  private String language;
  private String string;

  public LangString() {
    super();
  }
  
  public LangString(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String languageName = "language";
    String stringName = "string";
    String languageJSON = JSONUtil.getStringValue(json, languageName);
    String stringJSON = JSONUtil.getStringValue(json, stringName);
    language = languageJSON;
    string = stringJSON;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getString() {
    return string;
  }

  public void setString(String string) {
    this.string = string;
  }
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getLanguage() != null) {
      sb.append("<language>" + this.getLanguage() + "</language>");
    }
    if (this.getString() != null) {
      sb.append("<string>" + this.getString() + "</string>");
    }
    return sb.toString();
  }
}

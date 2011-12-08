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

package org.sakaiproject.nakamura.lom.type;

import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.elements.LangString;

public abstract class LangStringType extends Serialize {
  private LangString langString;
 
  public LangStringType() {
    super();
  }
  
  public LangStringType(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String langStringName = "langstring";
    JSONObject langStringObject = JSONUtil.getJSONObject(json, langStringName);
    if (langStringObject == null) {
      String content = JSONUtil.getStringValue(json, langStringName);
      if (content == null) {
        LangString ls = new LangString(json);
        if (ls.getLanguage() != null || ls.getString() != null) {
          langString = ls;
        }
      } else {
        langString = new LangString();
        langString.setString(content);
      }
    } else {
      langString = new LangString(langStringObject);
    }
  }
  
  public LangString getLangString() {
    return langString;
  }

  public void setLangString(LangString langString) {
    this.langString = langString;
  }
}

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
import org.sakaiproject.nakamura.lom.elements.Source;
import org.sakaiproject.nakamura.lom.elements.Value;

public abstract class VocabularyType extends Serialize {

  private Source source;
  private Value value;

  public VocabularyType() {
    super();
  }
  
  public VocabularyType(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String sourceName = "source";
    String valueName = "value";
    JSONObject sourceJSON = JSONUtil.getJSONObject(json, sourceName);
    if (sourceJSON == null) {
      String s = JSONUtil.getStringValue(json, sourceName);
      this.setSource(s);
    } else {
      source = new Source(sourceJSON);
    }

    JSONObject valueJSON = JSONUtil.getJSONObject(json, valueName);
    if (valueJSON == null) {
      String v = JSONUtil.getStringValue(json, valueName);
      this.setValue(v);
    } else {
      value = new Value(valueJSON);
    }
  }
  
  public String getSource() {
    if (source != null) {
      return source.getLangString().getString();
    } else {
      return null;
    }
  }

  public void setSource(String source) {
    Source s = new Source();
    LangString l = new LangString();
    l.setString(source);
    s.setLangString(l);
    this.source = s;
  }

  public String getValue() {
    if (value == null) {
      return null;
    }
    return value.getLangString().getString();
  }

  public void setValue(String value) {
    LangString l = new LangString();
    l.setString(value);
    Value v = new Value();
    v.setLangString(l);
    this.value = v;
  }
  
  public abstract String[] getLOMVocabulary ();
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getSource() != null) {
      sb.append("<source>" + this.getSource() + "</source>");
    }
    if (this.getValue() != null) {
      sb.append("<value>" + this.getValue() + "</value>");
    }
    return sb.toString();
  }
}

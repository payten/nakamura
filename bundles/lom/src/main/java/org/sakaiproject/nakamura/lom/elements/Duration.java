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

public class Duration extends Serialize{
  private String duration;
  private Description description;

  public Duration() {
    super();
  }
  
  public Duration(JSONObject json) {
    super(json);
  }
 
  @Override
  protected void init() {
    String durationName = "duration";
    String descriptionName = "description";
    
    duration = JSONUtil.getStringValue(json, durationName);
    
    JSONObject descriptionJSON = JSONUtil.getJSONObject(json, descriptionName);
    if (descriptionJSON != null) {
      description = new Description(descriptionJSON);
    }
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
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
    if (this.getDuration() != null) {
      sb.append("<duration>" + this.getDuration() + "</duration>");
    }
    if (this.getDescription() != null) {
      sb.append(this.getDescription().generateXML());
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<duration>" + sb.toString() + "</duration>");
  }
}

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
import org.sakaiproject.nakamura.lom.type.Serialize;

public class File extends Serialize {
  private String href;
  private Metadata metadata;
  
  public File() {
    super();
  }
  
  public File(JSONObject json) {
    this.json = json;
    this.init();
  }
  
  public void init() {
    String hrefName = "href";
    String metadataName = "metadata";
    String lomName = "lom";
    href = JSONUtil.getStringValue(json, hrefName);
    
    JSONObject metaJSON = JSONUtil.getJSONObject(json, metadataName);
    if (metaJSON != null) {
      metadata = new Metadata(metaJSON);
    } else {
      if (JSONUtil.getJSONObject(json, lomName) != null) {
        metadata = new Metadata(json);
      }
    }
  }
  
  public String getHref() {
    return href;
  }
  
  public void setHref(String href) {
    this.href = href;
  }
  
  public Metadata getMetadata() {
    return metadata;
  }
  
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public String generateXML() {
    StringBuilder head = new StringBuilder("<file");
    StringBuilder sb = new StringBuilder("");
    if (this.getHref() != null) {
      head.append(" href=\"" + this.getHref() + "\"");
    }
    if (this.getMetadata() != null) {
      sb.append(this.getMetadata().generateXML());
    }
    if (sb.toString().equals("") && head.equals("<file")) {
      return "";
    }
    return new String(head.toString() + ">" + sb.toString() + "</file>");
  }
}

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

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.elements.Contribute;
import org.sakaiproject.nakamura.lom.elements.Status;
import org.sakaiproject.nakamura.lom.elements.Version;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class LifeCycle extends Serialize {
  private Version version;
  private Status status;
  private List<Contribute> contribute;

  public LifeCycle() {
    super();
  }
  
  public LifeCycle(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String versionName = "version";
    String statusName = "status";
    String contributeName = "contribute";
    JSONObject versionJSON = JSONUtil.getJSONObject(json, versionName);
    if (versionJSON != null) {
      version = new Version(versionJSON);
    }
    
    JSONObject statusJSON = JSONUtil.getJSONObject(json, statusName);
    if (statusJSON != null) {
      status = new Status(statusJSON);
    }
    
    JSONObject contributeJSON = JSONUtil.getJSONObject(json, contributeName);
    if (contributeJSON == null) {
      JSONArray contributeArray = JSONUtil.getJSONArray(json, contributeName);
      if (contributeArray != null) {
        for (int i = 0; i < contributeArray.length(); i++) {
          JSONObject object = contributeArray.optJSONObject(i);
          if (object != null) {
            addContribute(new Contribute(object, Contribute.CONTRIBUTETYPE.LIFECYCLE));
          }
        }
      }
    } else {
      addContribute(new Contribute(contributeJSON, Contribute.CONTRIBUTETYPE.LIFECYCLE));
    }
      

  }
 
  public Version getVersion() {
    return version;
  }
  
  public void setVersion(Version version) {
    this.version = version;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public List<Contribute> getContribute() {
    return contribute;
  }

  public void addContribute (Contribute c) {
    if (contribute == null) {
      contribute = new ArrayList<Contribute>();
    }
    contribute.add(c);
  }
  
  public void setContribute(List<Contribute> contribute) {
    this.contribute = contribute;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getVersion() != null) {
      sb.append(this.getVersion().generateXML());
    }
    if (this.getStatus() != null) {
      sb.append(this.getStatus().generateXML());
    }
    if (this.getContribute() != null) {
      for (int i = 0; i < this.getContribute().size(); i++) {
        sb.append(this.getContribute().get(i).generateXML());
      }
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<lifeCycle>" + sb.toString() + "</lifeCycle>");
  }
}

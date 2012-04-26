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
import org.sakaiproject.nakamura.lom.elements.Duration;
import org.sakaiproject.nakamura.lom.elements.InstallationRemarks;
import org.sakaiproject.nakamura.lom.elements.OtherPlatformRequirements;
import org.sakaiproject.nakamura.lom.elements.Requirement;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class Technical extends Serialize{
  private List<String> format;
  private String size;
  private List<String> location;
  private List<Requirement> requirement;
  private InstallationRemarks installationRemarks;
  private OtherPlatformRequirements otherPlatformRequirements;
  private Duration duration;

  public Technical() {
    super();
  }
  
  public Technical(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String formatName = "format";
    String sizeName = "size";
    String locationName = "location";
    String requirementName = "requirement";
    String installationRemarksName = "installationRemarks";
    String otherPlatformRequirementsName = "otherPlatformRequirements";
    String durationName = "duration";
    
    String formatValue = JSONUtil.getStringValue(json, formatName);
    if (formatValue == null) {
      JSONArray formatArray = JSONUtil.getJSONArray(json, formatName);
      if (formatArray != null) {
        for (int i = 0; i < formatArray.length(); i++) {
          String object = formatArray.optString(i);
          if (object != null)
            addFormat(object);
        }
      }
    } else {
      addFormat(formatValue);
    }
    
    size = JSONUtil.getStringValue(json, sizeName);
    
    String locationValue = JSONUtil.getStringValue(json, locationName);
    if (locationValue == null) {
      JSONArray locationArray = JSONUtil.getJSONArray(json, locationName);
      if (locationArray != null) {
        for (int i = 0; i < locationArray.length(); i++) {
          String object = locationArray.optString(i);
          if (object != null)
            addLocation(object);
        }
      }
    } else {
      addLocation(locationValue);
    }
    
    JSONObject requirementValue = JSONUtil.getJSONObject(json, requirementName);
    if (requirementValue == null) {
      JSONArray requirementArray = JSONUtil.getJSONArray(json, requirementName);
      if (requirementArray != null) {
        for (int i = 0; i < requirementArray.length(); i++) {
          JSONObject object = requirementArray.optJSONObject(i);
          if (object != null)
            new Requirement(object);
        }
      }
    } else {
      addRequirement(new Requirement(requirementValue));
    }
    
    JSONObject installationJSON = JSONUtil.getJSONObject(json, installationRemarksName);
    if (installationJSON != null)
      this.installationRemarks = new InstallationRemarks(installationJSON);
    
    JSONObject otherPlatformRequirementsJSON = JSONUtil.getJSONObject(json, otherPlatformRequirementsName);
    if (otherPlatformRequirementsJSON != null)
      this.otherPlatformRequirements = new OtherPlatformRequirements(otherPlatformRequirementsJSON);
    
    JSONObject durationJSON = JSONUtil.getJSONObject(json, durationName);
    if (durationJSON != null)
      this.duration = new Duration(durationJSON);
  }

  public List<String> getFormat() {
    return format;
  }
  
  public void addFormat(String f) {
    if (format == null) {
      format = new ArrayList<String>();
    }
    format.add(f);
  }

  public void setFormat(List<String> format) {
    this.format = format;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public List<String> getLocation() {
    return location;
  }

  public void addLocation(String l) {
    if (location == null) {
      location = new ArrayList<String>();
    }
    location.add(l);
  }
  
  public void setLocation(List<String> location) {
    this.location = location;
  }

  public List<Requirement> getRequirement() {
    return requirement;
  }

  public void setRequirement(List<Requirement> requirement) {
    this.requirement = requirement;
  }
  
  public void addRequirement(Requirement r) {
    if(requirement == null) {
      requirement = new ArrayList<Requirement>();
    }
    requirement.add(r);
  }

  public InstallationRemarks getInstallationRemarks() {
    return installationRemarks;
  }

  public void setInstallationRemarks(InstallationRemarks installationRemarks) {
    this.installationRemarks = installationRemarks;
  }

  public OtherPlatformRequirements getOtherPlatFormRequirements() {
    return otherPlatformRequirements;
  }

  public void setOtherPlatFormRequirements(
      OtherPlatformRequirements otherPlatformRequirements) {
    this.otherPlatformRequirements = otherPlatformRequirements;
  }

  public Duration getDuration() {
    return duration;
  }

  public void setDuration(Duration duration) {
    this.duration = duration;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getFormat() != null) {
      for (int i = 0; i < this.getFormat().size(); i++) {
        sb.append("<format>" + this.getFormat().get(i) + "</format>");
      }
    }
    if (this.getSize() != null) {
      sb.append("<size>" + this.getSize() + "</size>");
    }
    if (this.getLocation() != null) {
      for (int i = 0; i < this.getLocation().size(); i++) {
        sb.append("<location>" + this.getLocation().get(i) + "</location>");
      }
    }
    if (this.getDuration() != null) {
      sb.append(this.getDuration().generateXML());
    }
    if (this.getRequirement() != null) {
      for (int i = 0; i < this.getRequirement().size(); i++) {
        sb.append(this.getRequirement().get(i).generateXML());
      }
    }
    if (this.getInstallationRemarks() != null) {
      sb.append(this.getInstallationRemarks().generateXML());
    }
    if (this.getOtherPlatFormRequirements() != null) {
      sb.append(this.getOtherPlatFormRequirements().generateXML());
    }
    
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<technical>" + sb.toString() + "</technical>");
  }
}

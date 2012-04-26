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
import org.sakaiproject.nakamura.lom.elements.Description;
import org.sakaiproject.nakamura.lom.elements.Keyword;
import org.sakaiproject.nakamura.lom.elements.Purpose;
import org.sakaiproject.nakamura.lom.elements.TaxonPath;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class Classification extends Serialize {
  private Purpose purpose;
  private List<TaxonPath> taxonPath;
  private Description description;
  private List<Keyword> keyword;

  public Classification() {
    super();
  }
  
  public Classification(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String purposeName = "purpose";
    String taxonPathName = "taxonPath";
    String descriptionName = "description";
    String keywordName = "keyword";
    JSONObject purposeJSON = JSONUtil.getJSONObject(json, purposeName);
    if (purposeJSON != null) {
      purpose = new Purpose(purposeJSON);
    }
    
    JSONObject taxonPathJSON = JSONUtil.getJSONObject(json, taxonPathName);
    if (taxonPathJSON == null) {
      JSONArray taxonPathArray = JSONUtil.getJSONArray(json, taxonPathName);
      if (taxonPathArray != null) {
        for (int i = 0; i < taxonPathArray.length(); i++) {
          JSONObject object = taxonPathArray.optJSONObject(i);
          if (object != null) {
            addTaxonPath(new TaxonPath(object));
          }
        }
      }
    } else {
      addTaxonPath(new TaxonPath(taxonPathJSON));
    }
    
    JSONObject descriptionJSON = JSONUtil.getJSONObject(json, descriptionName);
    if (descriptionJSON != null) {
      description = new Description(descriptionJSON);
    }
    
    JSONObject keywordJSON = JSONUtil.getJSONObject(json, keywordName);
    if (keywordJSON != null) {
      addKeyword(new Keyword(keywordJSON));
    } else {
      JSONArray keywordArray = JSONUtil.getJSONArray(json, keywordName);
      if (keywordArray != null) {
        for (int i = 0; i < keywordArray.length(); i++) {
          JSONObject object = keywordArray.optJSONObject(i);
          if (object != null) {
            addKeyword(new Keyword(object));
          }
        }
      }
    }

  }
  
  public Purpose getPurpose() {
    return purpose;
  }

  public void setPurpose(Purpose purpose) {
    this.purpose = purpose;
  }

  public List<TaxonPath> getTaxonPath() {
    return taxonPath;
  }
  
  public void addTaxonPath (TaxonPath tp) {
    if (taxonPath == null) {
      taxonPath = new ArrayList<TaxonPath>();
    }
    taxonPath.add(tp);
  }

  public void setTaxonPath(List<TaxonPath> taxonPath) {
    this.taxonPath = taxonPath;
  }

  public Description getDescription() {
    return description;
  }

  public void setDescription(Description description) {
    this.description = description;
  }

  public List<Keyword> getKeyword() {
    return keyword;
  }
  
  public void addKeyword(Keyword k) {
    if (keyword == null) {
      keyword = new ArrayList<Keyword>();
    }
    keyword.add(k);
  }

  public void setKeyword(List<Keyword> keyword) {
    this.keyword = keyword;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getTaxonPath() != null) {
      for (int i = 0; i < this.getTaxonPath().size(); i++) {
        sb.append(this.getTaxonPath().get(i).generateXML());
      }
    }
    if (this.getPurpose() != null) {
      sb.append(this.getPurpose().generateXML());
    }
    if (this.getKeyword() != null) {
      for (int i = 0; i < this.getKeyword().size(); i++) {
        sb.append(this.getKeyword().get(i).generateXML());
      }
    }
    if (this.getDescription() != null) {
      sb.append(this.getDescription().generateXML());
    }
    
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<classification>" + sb.toString() + "</classification>");
  }
}

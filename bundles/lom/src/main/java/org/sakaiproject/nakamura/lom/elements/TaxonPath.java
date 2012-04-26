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

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class TaxonPath extends Serialize {

  private Source source;
  private List<Taxon> taxon;

  public TaxonPath() {
    super();
  }
  
  public TaxonPath(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String sourceName = "source";
    String taxonName = "taxon";
    JSONObject sourceJSON = JSONUtil.getJSONObject(json, sourceName);
    if (sourceJSON != null) {
      source = new Source(sourceJSON);
    }
    
    JSONObject taxonJSON = JSONUtil.getJSONObject(json, taxonName);
    if (taxonJSON == null) {
      JSONArray taxonArray = JSONUtil.getJSONArray(json, taxonName);
      if (taxonArray != null) {
        for (int i = 0; i < taxonArray.length(); i++) {
          JSONObject object = taxonArray.optJSONObject(i);
          if (object != null) {
            addTaxon(new Taxon(object));
          }
        }
      }
    } else {
      addTaxon(new Taxon(taxonJSON));
    }
  }
 
  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }

  public List<Taxon> getTaxon() {
    return taxon;
  }
  
  public void addTaxon(Taxon t) {
    if (taxon == null) {
      taxon = new ArrayList<Taxon> ();
    }
    taxon.add(t);
  }

  public void setTaxon(List<Taxon> taxon) {
    this.taxon = taxon;
  }

  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getSource() != null) {
      sb.append(this.getSource().generateXML());
    }
    if (this.getTaxon() != null) {
      for (int i = 0; i < this.getTaxon().size(); i++) {
        sb.append(this.getTaxon().get(i).generateXML());
      }
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<taxonPath>" + sb.toString() + "</taxonPath>");
  }
}

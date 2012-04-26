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
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.xml.XML;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class LOMRoot extends Serialize{
  private General general;
  private LifeCycle lifeCycle;
  private Technical technical;
  private MetaMetadata metaMetadata;
  private List<Educational> educational;
  private Rights rights;
  private List<Relation> relation;
  private List<Annotation> annotation;
  private List<Classification> classification;

  public LOMRoot () {
    super();
  }
  
  public LOMRoot(JSONObject json) {
    this.setJSON(json, "lom");
    this.init();
  }
  
  public LOMRoot(String xmlcontent) throws JSONException {
    this(XML.toJSONObject(xmlcontent));
  }
  
  @Override
  protected void init() {
    String generalName = "general";
    String lifeCycleName = "lifeCycle";
    String technicalName = "technical";
    String metaMetadataName = "metaMetadata";
    String educationalName = "educational";
    String rightsName = "rights";
    String relationName = "relation";
    String annotationName = "annotation";
    String classificationName = "classification";
    JSONObject generalJSON = JSONUtil.getJSONObject(json, generalName);
    if (generalJSON != null) {
      general = new General(generalJSON);
    }
    
    JSONObject lifeCycleObject = JSONUtil.getJSONObject(json, lifeCycleName);
    if (lifeCycleObject != null){
      lifeCycle = new LifeCycle(lifeCycleObject);
    }
    
    JSONObject technicalJSON = JSONUtil.getJSONObject(json, technicalName);
    if (technicalJSON != null) {
      technical = new Technical(technicalJSON);
    }
    
    JSONObject metaMetadataJSON = JSONUtil.getJSONObject(json, metaMetadataName);
    if (metaMetadataJSON != null) {
      metaMetadata = new MetaMetadata(metaMetadataJSON);
    }
    
    JSONObject educationalJSON = JSONUtil.getJSONObject(json, educationalName);
    if (educationalJSON == null) {
      JSONArray educationalArray = JSONUtil.getJSONArray(json, educationalName);
      if (educationalArray != null){
        for (int i = 0; i < educationalArray.length(); i++) {
          JSONObject object = educationalArray.optJSONObject(i);
          if (object != null) {
            Educational e = new Educational(object);
            addEducational(e);
          }
        }
      }
    } else {
      addEducational(new Educational(educationalJSON));
    }
    
    JSONObject rightsJSON = JSONUtil.getJSONObject(json, rightsName);
    if (rightsJSON != null) {
      rights = new Rights(rightsJSON);
    }
    
    JSONObject relationJSON = JSONUtil.getJSONObject(json, relationName);
    if (relationJSON == null) {
      JSONArray relationArray = JSONUtil.getJSONArray(json, relationName);
      if (relationArray != null) {
        for (int i = 0; i < relationArray.length(); i++) {
          JSONObject object = relationArray.optJSONObject(i);
          if (object != null) {
            addRelation(new Relation(object));
          }
        }
      }
    } else {
      addRelation(new Relation(relationJSON));
    }
    
    JSONObject annotationJSON = JSONUtil.getJSONObject(json, annotationName);
    if (annotationJSON == null) {
      JSONArray annotationArray = JSONUtil.getJSONArray(json, annotationName);
      if (annotationArray != null) {
        for (int i = 0; i < annotationArray.length(); i++) {
          JSONObject object = annotationArray.optJSONObject(i);
          if (object != null) {
            addAnnotation(new Annotation(object));
          }
        }
      }
    } else {
      addAnnotation(new Annotation(annotationJSON));
    }
    
    JSONObject classificationJSON = JSONUtil.getJSONObject(json, classificationName);
    if (classificationJSON == null) {
      JSONArray classificationArray = JSONUtil.getJSONArray(json, classificationName);
      if (classificationArray != null) {
        for (int i = 0; i < classificationArray.length(); i++) {
          JSONObject object = classificationArray.optJSONObject(i);
          if (object != null) {
            addClassification(new Classification(object));
          }
        }
      }
    } else {
      addClassification(new Classification(classificationJSON));
    }
  }
  
  private void setJSON(JSONObject json, String lomName) {
    JSONObject lomObject = JSONUtil.getJSONObject(json, lomName);
    this.json = lomObject;
    if (lomObject == null) {
      this.json = new JSONObject();
    }
  }
  
  public General getGeneral() {
    return general;
  }
  
  public void setGeneral(General general) {
    this.general = general;
  }
  
  public LifeCycle getLifeCycle() {
    return lifeCycle;
  }

  public void setLifeCycle(LifeCycle lifeCycle) {
    this.lifeCycle = lifeCycle;
  }

  public Technical getTechnical() {
    return technical;
  }

  public void setTechnical(Technical technical) {
    this.technical = technical;
  }
  
  public MetaMetadata getMetaMetadata() {
    return metaMetadata;
  }

  public void setMetaMetadata(MetaMetadata metaMetadata) {
    this.metaMetadata = metaMetadata;
  }

  public List<Educational> getEducational() {
    return educational;
  }

  public void addEducational(Educational e) {
    if (educational == null) {
      educational = new ArrayList<Educational>();
    }
    educational.add(e);
  }
  
  public void setEducational(List<Educational> educational) {
    this.educational = educational;
  }

  public Rights getRights() {
    return rights;
  }

  public void setRights(Rights rights) {
    this.rights = rights;
  }

  public List<Relation> getRelation() {
    return relation;
  }
  
  public void addRelation(Relation r) {
    if (relation == null) {
      relation = new ArrayList<Relation>();
    }
    relation.add(r);
  }

  public void setRelation(List<Relation> relation) {
    this.relation = relation;
  }

  public List<Annotation> getAnnotation() {
    return annotation;
  }

  public void addAnnotation(Annotation a) {
    if (annotation == null) {
      annotation = new ArrayList<Annotation> ();
    }
    annotation.add(a);
  }
  
  public void setAnnotation(List<Annotation> annotation) {
    this.annotation = annotation;
  }

  public List<Classification> getClassification() {
    return classification;
  }
  
  public void addClassification(Classification c) {
    if (classification == null) {
      classification = new ArrayList<Classification>();
    }
    classification.add(c);
  }

  public void setClassification(List<Classification> classification) {
    this.classification = classification;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getGeneral() != null) {
      sb.append(this.getGeneral().generateXML());
    }
    if (this.getLifeCycle() != null) {
      sb.append(this.getLifeCycle().generateXML());
    }
    if (this.getMetaMetadata() != null) {
      sb.append(this.getMetaMetadata().generateXML());
    }
    if (this.getTechnical() != null) {
      sb.append(this.getTechnical().generateXML());
    }
    if (this.getEducational() != null) {
      for (int i = 0; i < this.getEducational().size(); i++) {
        sb.append(this.getEducational().get(i).generateXML());
      }
    }
    if (this.getRights() != null) {
      sb.append(this.getRights().generateXML());
    }
    if (this.getRelation() != null) {
      for (int i = 0; i < this.getRelation().size(); i++) {
        sb.append(this.getRelation().get(i).generateXML());
      }
    }
    if (this.getAnnotation() != null) {
      for (int i = 0; i < this.getAnnotation().size(); i++) {
        sb.append(this.getAnnotation().get(i).generateXML());
      }
    }
    if (this.getClassification() != null) {
      for (int i = 0; i < this.getClassification().size(); i++) {
        sb.append(this.getClassification().get(i).generateXML());
      }
    }
    return new String("<lom>" + sb.toString() + "</lom>");
  }
}

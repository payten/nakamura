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
import org.sakaiproject.nakamura.lom.elements.Context;
import org.sakaiproject.nakamura.lom.elements.Description;
import org.sakaiproject.nakamura.lom.elements.Difficulty;
import org.sakaiproject.nakamura.lom.elements.IntendedEndUserRole;
import org.sakaiproject.nakamura.lom.elements.InteractivityLevel;
import org.sakaiproject.nakamura.lom.elements.InteractivityType;
import org.sakaiproject.nakamura.lom.elements.LearningResourceType;
import org.sakaiproject.nakamura.lom.elements.SemanticDensity;
import org.sakaiproject.nakamura.lom.elements.TypicalAgeRange;
import org.sakaiproject.nakamura.lom.elements.TypicalLearningTime;
import org.sakaiproject.nakamura.lom.type.JSONUtil;
import org.sakaiproject.nakamura.lom.type.Serialize;

import java.util.ArrayList;
import java.util.List;

public class Educational extends Serialize {
  private InteractivityType interactivityType;
  private List<LearningResourceType> learningResourceType;
  private InteractivityLevel interactivityLevel;
  private SemanticDensity semanticDensity;
  private List<IntendedEndUserRole> intendedEndUserRole;
  private List<Context> context;
  private List<TypicalAgeRange> typicalAgeRange;
  private Difficulty difficulty;
  private TypicalLearningTime typicalLearningTime;
  private List<Description> description;
  private List<String> language;

  public Educational() {
    super();
  }
  
  public Educational(JSONObject json) {
    super(json);
  }

  @Override
  protected void init() {
    String interactivityTypeName = "interactivityType";
    String learningResourceTypeName = "learningResourceType";
    String interactivityLevelName = "interactivityLevel";
    String semanticDensityName = "semanticDensity";
    String intendedEndUserRoleName = "intendedEndUserRole";
    String contextName = "context";
    String typicalAgeRangeName = "typicalAgeRange";
    String difficultyName = "difficulty";
    String typicalLearningTimeName = "typicalLearningTime";
    String descriptionName = "description";
    String languageName = "language";
    JSONObject interactivityTypeJSON = JSONUtil.getJSONObject(json, interactivityTypeName);
    if (interactivityTypeJSON != null) {
      interactivityType = new InteractivityType(interactivityTypeJSON);
    } 
    
    JSONObject learningResourceTypeJSON = JSONUtil.getJSONObject(json, learningResourceTypeName);
    if (learningResourceTypeJSON == null) {
      JSONArray learningResourceTypeArray = JSONUtil.getJSONArray(json, learningResourceTypeName);
      if (learningResourceTypeArray != null) {
        for (int i = 0; i < learningResourceTypeArray.length(); i++){
          JSONObject object = learningResourceTypeArray.optJSONObject(i);
          if (object != null) {
            addLearningResourceType(new LearningResourceType(object));
          }
        }
      }
    } else {
      addLearningResourceType(new LearningResourceType(learningResourceTypeJSON));
    }
    
    JSONObject interactivityLevelJSON = JSONUtil.getJSONObject(json, interactivityLevelName);
    if (interactivityLevelJSON != null) {
      interactivityLevel = new InteractivityLevel(interactivityLevelJSON);
    }
    
    JSONObject semanticDensityJSON = JSONUtil.getJSONObject(json, semanticDensityName);
    if (semanticDensityJSON != null) {
      semanticDensity = new SemanticDensity(semanticDensityJSON);
    }
    
    JSONObject intendedEndUserRoleJSON = JSONUtil.getJSONObject(json, intendedEndUserRoleName);
    if (intendedEndUserRoleJSON == null) {
      JSONArray intendedEndUserRoleArray = JSONUtil.getJSONArray(json, intendedEndUserRoleName);
      if (intendedEndUserRoleArray != null) {
        for (int i = 0; i < intendedEndUserRoleArray.length(); i++){
          JSONObject object = intendedEndUserRoleArray.optJSONObject(i);
          if (object != null) {
            addIntendedEndUserRole(new IntendedEndUserRole(object));
          }
        }
      }
    } else {
      addIntendedEndUserRole(new IntendedEndUserRole(intendedEndUserRoleJSON));
    }
    
    JSONObject contextJSON = JSONUtil.getJSONObject(json, contextName);
    if (intendedEndUserRoleJSON == null) {
      JSONArray contextArray = JSONUtil.getJSONArray(json, contextName);
      if (contextArray != null) {
        for (int i = 0; i < contextArray.length(); i++){
          JSONObject object = contextArray.optJSONObject(i);
          if (object != null) {
            addContext(new Context(object));
          }
        }
      }
    } else {
      addContext(new Context(contextJSON));
    }
    
    JSONObject typicalAgeRangeJSON = JSONUtil.getJSONObject(json, typicalAgeRangeName);
    if (typicalAgeRangeJSON == null) {
      JSONArray typicalAgeRangeArray = JSONUtil.getJSONArray(json, typicalAgeRangeName);
      if (typicalAgeRangeArray != null) {
        for (int i = 0; i < typicalAgeRangeArray.length(); i++){
          JSONObject object = typicalAgeRangeArray.optJSONObject(i);
          if (object != null) {
            addTypicalAgeRange(new TypicalAgeRange(object));
          }
        }
      }
    } else {
      addTypicalAgeRange(new TypicalAgeRange(typicalAgeRangeJSON));
    }
    
    JSONObject difficultyJSON = JSONUtil.getJSONObject(json, difficultyName);
    if (difficultyJSON != null) {
      difficulty = new Difficulty(difficultyJSON);
    }

    JSONObject typicalLearningTimeJSON = JSONUtil.getJSONObject(json, typicalLearningTimeName);
    if (typicalLearningTimeJSON != null) {
      typicalLearningTime = new TypicalLearningTime(typicalLearningTimeJSON);
    }
    
    JSONObject descriptionJSON = JSONUtil.getJSONObject(json, descriptionName);
    if (descriptionJSON == null) {
      JSONArray descriptionArray = JSONUtil.getJSONArray(json, descriptionName);
      if (descriptionArray != null) {
        for (int i = 0; i < descriptionArray.length(); i++){
          JSONObject object = descriptionArray.optJSONObject(i);
          if (object != null) {
            addDescription(new Description(object));
          }
        }
      }
    } else {
      addDescription(new Description(descriptionJSON));
    }
    
    String languageJSON = JSONUtil.getStringValue(json, languageName);
    if (languageJSON == null) {
      JSONArray languageArray = JSONUtil.getJSONArray(json, languageName);
      if (languageArray != null) {
        for (int i = 0; i < languageArray.length(); i++){
          String object = languageArray.optString(i);
          if (object != null) {
            addLanguage(object);
          }
        }
      }
    } else {
      addLanguage(languageJSON);
    }
  }
 
  public InteractivityType getInteractivityType() {
    return interactivityType;
  }
  
  public void setInteractivityType(InteractivityType interactivityType) {
    this.interactivityType = interactivityType;
  }

  public List<LearningResourceType> getLearningResourceType() {
    return learningResourceType;
  }
  
  public void addLearningResourceType(LearningResourceType l) {
    if (learningResourceType == null) {
      learningResourceType = new ArrayList<LearningResourceType>();
    }
    learningResourceType.add(l);
  }

  public void setLearningResourceType(List<LearningResourceType> learningResourceType) {
    this.learningResourceType = learningResourceType;
  }

  public InteractivityLevel getInteractivityLevel() {
    return interactivityLevel;
  }

  public void setInteractivityLevel(InteractivityLevel interactivityLevel) {
    this.interactivityLevel = interactivityLevel;
  }

  public SemanticDensity getSemanticDensity() {
    return semanticDensity;
  }

  public void setSemanticDensity(SemanticDensity semanticDensity) {
    this.semanticDensity = semanticDensity;
  }

  public List<IntendedEndUserRole> getIntendedEndUserRole() {
    return intendedEndUserRole;
  }
  
  public void addIntendedEndUserRole(IntendedEndUserRole i) {
    if (intendedEndUserRole == null) {
      intendedEndUserRole = new ArrayList<IntendedEndUserRole>();
    }
    intendedEndUserRole.add(i);
  }

  public void setIntendedEndUserRole(List<IntendedEndUserRole> intendedEndUserRole) {
    this.intendedEndUserRole = intendedEndUserRole;
  }

  public List<Context> getContext() {
    return context;
  }
  
  public void addContext(Context c) {
    if (context == null) {
      context = new ArrayList<Context>();
    }
    context.add(c);
  }

  public void setContext(List<Context> context) {
    this.context = context;
  }

  public List<TypicalAgeRange> getTypicalAgeRange() {
    return typicalAgeRange;
  }
  
  public void addTypicalAgeRange(TypicalAgeRange t) {
    if (typicalAgeRange == null) {
      typicalAgeRange = new ArrayList<TypicalAgeRange>();
    }
    typicalAgeRange.add(t);
  }

  public void setTypicalAgeRange(List<TypicalAgeRange> typicalAgeRange) {
    this.typicalAgeRange = typicalAgeRange;
  }

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(Difficulty difficulty) {
    this.difficulty = difficulty;
  }

  public TypicalLearningTime getTypicalLearningTime() {
    return typicalLearningTime;
  }

  public void setTypicalLearningTime(TypicalLearningTime typicalLearningTime) {
    this.typicalLearningTime = typicalLearningTime;
  }

  public List<Description> getDescription() {
    return description;
  }
  
  public void addDescription(Description d) {
    if (description == null) {
      description = new ArrayList<Description> ();
    }
    description.add(d);
  }

  public void setDescription(List<Description> description) {
    this.description = description;
  }

  public List<String> getLanguage() {
    return language;
  }

  public void addLanguage(String l) {
    if (language == null){
      language = new ArrayList<String>();
    }
    language.add(l);
  }
  
  public void setLanguage(List<String> language) {
    this.language = language;
  }
  
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder("");
    if (this.getInteractivityType() != null)
      sb.append(this.getInteractivityType().generateXML());
    if (this.getLearningResourceType() != null) {
      for (int i = 0; i < this.getLearningResourceType().size(); i++) {
        sb.append(this.getLearningResourceType().get(i).generateXML());
      }
    }
    if (this.getInteractivityLevel() != null) {
      sb.append(this.getInteractivityLevel().generateXML());
    }
    if (this.getSemanticDensity() != null) {
      sb.append(this.getSemanticDensity().generateXML());
    }
    if (this.getIntendedEndUserRole() != null) {
      for (int i = 0; i < this.getIntendedEndUserRole().size(); i++) {
        sb.append(this.getIntendedEndUserRole().get(i).generateXML());
      }
    }
    if (this.getContext() != null) {
      for (int i = 0; i < this.getContext().size(); i++) {
        sb.append(this.getContext().get(i).generateXML());
      }
    }
    if (this.getTypicalAgeRange() != null) {
      for (int i = 0; i < this.getTypicalAgeRange().size(); i++) {
        sb.append(this.getTypicalAgeRange().get(i).generateXML());
      }
    }
    if (this.getDifficulty() != null) {
      sb.append(this.getDifficulty().generateXML());
    }
    if (this.getTypicalLearningTime() != null) {
      sb.append(this.getTypicalLearningTime().generateXML());
    }
    if (this.getDescription() != null) {
      for (int i = 0; i < this.getDescription().size(); i++) {
        sb.append(this.getDescription().get(i).generateXML());
      }
    }
    if (this.getLanguage() != null) {
      for (int i = 0; i < this.getLanguage().size(); i++) {
        sb.append("<language>" + this.getLanguage().get(i)+ "</language>");
      }
    }
    if (sb.toString().equals("")) {
      return "";
    }
    return new String("<educational>" + sb.toString() + "</educational>");
  }
}

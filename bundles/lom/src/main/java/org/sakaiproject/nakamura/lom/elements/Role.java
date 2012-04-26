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
import org.sakaiproject.nakamura.lom.type.VocabularyType;

public class Role extends VocabularyType {

  private String[] vocabulary = new String[] {"author", "publisher", "unknown", "initiator", "terminator", "validator", "editor",
      "graphical designer", "techinical implementer", "content provider", "technical validator", "educational validator", "script writer",
      "instructional designer", "subject matter expert"
  };

  private String[] vocabularyForMetaData = new String[] {"creator", "validator"};
  
  
  private Contribute.CONTRIBUTETYPE belongsTo;

  public Role(Contribute.CONTRIBUTETYPE roleType) {
    super();
    this.belongsTo = roleType; 
  }
  
  public Role (JSONObject json, Contribute.CONTRIBUTETYPE roleType) {
    super(json);
    this.belongsTo = roleType; 
  }

  @Override
  public String[] getLOMVocabulary() {
    return vocabulary;
  }
  
  public String[] getLOMVocabularyForLifeCycle() {
    return vocabulary;
  }
  
  public String[] getLOMVocabularyForMetametaData() {
    return this.vocabularyForMetaData;
  }
  
  public Contribute.CONTRIBUTETYPE getRoleType() {
    return belongsTo;
  }
  
  @Override
  public String generateXML() {
    if (super.generateXML().equals("")) {
      return "";
    }
    return new String("<role>" + super.generateXML() + "</role>");
  }

}

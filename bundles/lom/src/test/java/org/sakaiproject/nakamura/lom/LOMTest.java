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

package org.sakaiproject.nakamura.lom;

import junit.framework.Assert;

import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.xml.XML;
import org.junit.Test;
import org.sakaiproject.nakamura.lom.basic.LOMRoot;

public class LOMTest {
  public String generalIdentifier1 = "<entry>0-226-10389-7</entry><catalog>ISBN</catalog>";
  public String generalIdentifier2 = "<entry>0-226-10389-2</entry><catalog>ISBN</catalog>";
  public String generalTitle = "<string>English</string><language>en</language>";
  public String generalDescription1 = "<string>English Desc1</string><language>en</language>";
  public String generalDescription2 = "<string>English Desc2</string><language>en</language>";
  public String generalKeyword1 = "<string>Keyword1</string><language>en</language>";
  public String generalKeyword2 = "<string>Keyword2</string><language>en</language>";
  public String generalCoverage1 = "<string>Coverage1</string><language>en</language>";
  public String generalCoverage2 = "<string>Coverage2</string><language>en</language>";
  public String generalStructure = "<source>LOMv1.0</source><value>atomic</value>";
  public String generalaggregationLevel = "<source>LOMv1.0</source><value>1</value>";
  
  public String generalContent = "<identifier>" + generalIdentifier1 + "</identifier>" + "<identifier>" + generalIdentifier2 + "</identifier>" + "<title>" + generalTitle + 
      "</title><language>en</language><language>zh</language>" +	"<description>" + generalDescription1 + "</description><description>" + generalDescription2 + "</description>" +
      "<keyword>" + generalKeyword1 + "</keyword><keyword>" + generalKeyword2 + "</keyword>" + "<coverage>" + generalCoverage1 + "</coverage><coverage>" + generalCoverage2 + 
      "</coverage>" + "<structure>" + generalStructure + "</structure><aggregationlevel>" + generalaggregationLevel + "</aggregationlevel>";
  
  public String lifecycleVersion = "<string>v1.0</string><language>en</language>";
  public String lifecycleStatus = "<source>LOMv1.0</source><value>final</value>";
  public String lifecycleContributeRole = "<source>LOMv1.0</source><value>author</value>";
  public String lifecycleDateDescription = "<string>English Desc1</string><language>en</language>";
  public String lifecycleDate = "<datetime>2006-11-14T17:04:00+08:00</datetime><description>" + lifecycleDateDescription + "</description>";
  public String lifecycleContribute = "<role>" + lifecycleContributeRole + "</role><entity>\nBEGIN:VCARD\nFN:Yushan\nORG:Sakai project\nEMAIL;TYPE=INTERNET:A@B\nADR:Cambridge\nEND:VCARD\n"
      +"</entity><entity>\nBEGIN:VCARD\nORG:Company 2\nEMAIL;TYPE=INTERNET:A@BB\nADR:Address 2\nEND:VCARD\n</entity>" + "<date>" + lifecycleDate + "</date>";
  
  public String lifecycleContent = "<version>" + lifecycleVersion + "</version><status>" + lifecycleStatus + "</status>" + "<contribute>" + lifecycleContribute + "</contribute>";

  public String metametadataIdentifier1 = "<entry>0-226-10389-7</entry><catalog>ISBN</catalog>";
  public String metametadataIdentifier2 = "<entry>0-226-10389-2</entry><catalog>ISBN</catalog>";
  public String metametadataRole = "<source>LOMv1.0</source><value>creator</value>";
  public String metametadataDateDescription = "<string>English Desc1</string><language>en</language>";
  public String metametadataContributeDate = "<datetime>2006-11-14T17:04:00+08:00</datetime><description>" + metametadataDateDescription + "</description>";
  public String metametadataContribute = "<role>" + metametadataRole + "</role><entity>\nBEGIN:VCARD\nFN:Yushan\nORG:Sakai project\nEMAIL;TYPE=INTERNET:A@B\nADR:Cambridge\nEND:VCARD\n" +
  		"</entity>" + "<entity>\nBEGIN:VCARD\nORG:Company2\nEMAIL;TYPE=INTERNET:A@BB\nADR:Address2\nEND:VCARD\n</entity><date>" + metametadataContributeDate + "</date>";
  
  
  public String metametadataContent = "<identifier>" + metametadataIdentifier1 + "</identifier><identifier>" + metametadataIdentifier2 + "</identifier>" +
  		"<contribute>" + metametadataContribute +"</contribute>" + "<metadataschema>schema1</metadataschema><metadataschema>schema2</metadataschema><language>zh</language>";

  public String technicalOrCompositeType1 = "<source>LOMv1.0</source><value>operating system</value>";
  public String technicalOrCompositeName1 = "<source>LOMv1.0</source><value>ms-windows</value>";
  public String technicalOrComposite1 = "<type>" + technicalOrCompositeType1 + "</type><name>" + technicalOrCompositeName1 + "</name><minimumVersion>Windows 98</minimumVersion>"
      + "<maximumVersion>Windows Server 2003</maximumVersion>";
  
  public String technicalOrCompositeType2 = "<source>LOMv1.0</source><value>operating system</value>";
  public String technicalOrCompositeName2 = "<source>LOMv1.0</source><value>macOS</value>";
  public String technicalOrComposite2 = "<type>" + technicalOrCompositeType2 + "</type><name>" + technicalOrCompositeName2 + "</name><minimumVersion>Mac OS IX</minimumVersion>";
  public String technicalRequirement = "<orComposite>" + technicalOrComposite1 + "</orComposite><orComposite>" + technicalOrComposite2 + "</orComposite>";
  public String technicalInstallationRemarks = "<string>Install 1</string><language>en</language>";
  public String technicalOtherPlatformRequirements = "<string>Other 1</string><language>en</language>";
  public String technicalDurationDescription = "<string>English Desc1</string><language>en</language>";
  public String technicalDuration = "<duration>P1Y2M3DT9H90M15S</duration><description>" + technicalDurationDescription + "</description>";
  
  public String technicalContent = "<format>text/html</format><format>text/pdf</format><size>10000</size><location>location1</location><location>location2</location>" +
  		"<requirement>" + technicalRequirement + "</requirement>" +
  		"<installationRemarks>" + technicalInstallationRemarks + "</installationRemarks><otherPlatformRequirements>" + technicalOtherPlatformRequirements + 
  		"</otherPlatformRequirements>" + "<duration>" + technicalDuration + "</duration>";

  public String educationalInteractivityType = "<source>LOMv1.0</source><value>active</value>";
  public String educationalLearningResourceType1 = "<source>LOMv1.0</source><value>exercise</value>";
  public String educationalLearningResourceType2 = "<source>LOMv1.0</source><value>exam</value>";
  public String educationalLearningResourceType3 = "<source>LOMv1.0</source><value>questionnaire</value>";
  public String educationalInteractivityLevel = "<source>LOMv1.0</source><value>medium</value>";
  public String educationalSemanticDensity = "<source>LOMv1.0</source><value>medium</value>";
  public String educationalIntendedEndUserRole1 = "<source>LOMv1.0</source><value>manager</value>";
  public String educationalIntendedEndUserRole2 = "<source>LOMv1.0</source><value>teacher</value>";
  public String educationalContext1 = "<source>LOMv1.0</source><value>higher education</value>";
  public String educationalContext2 = "<source>LOMv1.0</source><value>training</value>";
  public String educationalTypicalAgeRange1 = "<string>FOURTEEN TO EIGHTEEN</string><language>en</language>";
  public String educationalTypicalAgeRange2 = "<string>FOURTEEN TO EIGHTEEN</string><language>en</language>";
  public String educationalDifficulty = "<source>LOMv1.0</source><value>easy</value>";
  public String educationalTypicalLearningTimeDescription = "<string>English Desc1</string><language>en</language>";
  public String educationalTypicalLearningTime = "<duration>P1Y2M3DT9H90M15S</duration><description>"+ educationalTypicalLearningTimeDescription + "</description>";
  public String educationalDescription = "<string>English Desc1</string><language>en</language>";
  
  
  public String educationalContent = "<interactivityType>" + educationalInteractivityType +"</interactivityType><learningResourceType>" + 
      educationalLearningResourceType1 + "</learningResourceType><learningResourceType>" + educationalLearningResourceType2 + "</learningResourceType><learningResourceType>" +
      educationalLearningResourceType3 + "</learningResourceType><interactivityLevel>" + educationalInteractivityLevel + "</interactivityLevel><semanticDensity>" +
  		educationalSemanticDensity + "</semanticDensity><intendedEndUserRole>" + educationalIntendedEndUserRole1 + "</intendedEndUserRole><intendedEndUserRole>" + 
      educationalIntendedEndUserRole2 + "</intendedEndUserRole><context>" + educationalContext1 + "</context>" +
  		"<context>" + educationalContext2 + "</context><typicalAgeRange>" + educationalTypicalAgeRange1 + "</typicalAgeRange><typicalAgeRange>" +
  		educationalTypicalAgeRange2 + "</typicalAgeRange><difficulty>" + educationalDifficulty + "</difficulty>" +
  		"<typicalLearningTime>" + educationalTypicalLearningTime + "</typicalLearningTime>" +
  		"<description>" + educationalDescription + "</description><language>English</language><language>Chinese</language>";

  public String rightCost = "<source>LOMv1.0</source><value>yes</value>";
  public String rightCopyright = "<source>LOMv1.0</source><value>yes</value>";
  public String rightDescription = "<string>English Desc1</string><language>en</language>";
  
  public String rightContent = "<cost>" + rightCost + "</cost><copyrightAndOtherRestrictions>" + rightCopyright + "</copyrightAndOtherRestrictions>" +"<description>" +
      rightDescription + "</description>";

  public String relationKind = "<source>LOMv1.0</source><value>ispartof</value>";
  public String relationResourceIdentifier1 = "<entry>0-226-10389-7</entry><catalog>ISBN</catalog>";
  public String relationResourceIdentifier2 = "<entry>0-226-10389-2</entry><catalog>ISBN</catalog>";
  public String relationResourceDescription1 = "<string>English Desc1</string>";
  public String relationResourceDescription2 = "<string>English Desc2</string>";
  public String relationResource = "<identifier>" + relationResourceIdentifier1 + "</identifier><identifier>" + relationResourceIdentifier2 + "</identifier><description>" + 
      relationResourceDescription1 + "</description><description>" + relationResourceDescription2 + "</description>";
  
  public String relationContent = "<kind>" + relationKind + "</kind>" + "<resource>" + relationResource + "</resource>";
  		
  public String annotationDateDescription = "<string>English Desc1</string>";
  public String annotationDate = "<datetime>2006</datetime><description>" + annotationDateDescription + "</description>";
  public String annotationDescription = "<string>English Desc2</string>";
  
  public String annotationContent = "<entity>BEGIN:VCARD FN:Yushan ORG:Sakai project EMAIL;TYPE=INTERNET:A@B ADR:Cambridge END:VCARD</entity><date>" + annotationDate +
  		"</date><description>" + annotationDescription + "</description>";

  public String classificationPurpose = "<source>LOMv1.0</source><value>educational level</value>";
  public String classificationTaxonPathSource1 = "<string>ACM</string><language>en</language>";
  public String classificationTaxonPathTaxonEntry1 = "<string>medicine</string><language>en</language>";
  public String classificationTaxonPathTaxon1 = "<id>320</id><entry>" + classificationTaxonPathTaxonEntry1 + "</entry>";
  public String classificationTaxonPathTaxonEntry2 = "<string>physics</string><language>en</language>";
  public String classificationTaxonPathTaxon2 = "<id>4.3.2</id><entry>" + classificationTaxonPathTaxonEntry2 + "</entry>";
  public String classificationTaxonPath1 = "<source>" + classificationTaxonPathSource1 +"</source>" + "<taxon>" + classificationTaxonPathTaxon1 +"</taxon><taxon>" +
      classificationTaxonPathTaxon2 + "</taxon>";
  public String classificationTaxonPathSource2 = "<string>ACM2</string><language>en</language>";
  public String classificationTaxonPathTaxonEntry21 = "<string>medicine2</string><language>en</language>";
  public String classificationTaxonPathTaxon21 = "<id>320.2</id><entry>" + classificationTaxonPathTaxonEntry21 + "</entry>"; 
  public String classificationTaxonPathTaxonEntry22 = "<string>physics2</string><language>en</language>";
  public String classificationTaxonPathTaxon22 = "<id>4.3.2.2</id><entry>" + classificationTaxonPathTaxonEntry22 + "</entry>"; 
  public String classificationTaxonPath2 = "<source>" + classificationTaxonPathSource2 + "</source><taxon>" + classificationTaxonPathTaxon21 + "</taxon><taxon>" + 
      classificationTaxonPathTaxon22 + "</taxon>";
  public String classificationDescription = "<string>English Desc1</string><language>en</language>";
  public String classificationKeyword1 = "<string>Keyword1</string><language>en</language>";
  public String classificationKeyword2 = "<string>Keyword2</string><language>en</language>";
  
  public String classificationContent = "<purpose>" + classificationPurpose +"</purpose><taxonPath>" + classificationTaxonPath1 +
  		"</taxonPath><taxonPath>" + classificationTaxonPath2 +"</taxonPath><description>" + classificationDescription +
  		"</description><keyword>" + classificationKeyword1 + "</keyword><keyword>" + classificationKeyword2 + "</keyword>";

  public String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><lom><general>" + generalContent + "</general><lifecycle>" + lifecycleContent + 
      "</lifecycle><metaMetadata>" + metametadataContent + "</metaMetadata><technical>" + technicalContent + "</technical><educational>" + 
      educationalContent + "</educational><rights>"+ rightContent + "</rights><relation>" + relationContent + "</relation><annotation>" + annotationContent + 
      "</annotation><classification>" + classificationContent + "</classification></lom>";
  
  public LOMTest() {
    
  }
  @Test
  public void testLOM() throws Exception {
   LOMRoot lom = new LOMRoot(xmlContent);
   String xml = lom.generateXML();
   JSONObject lomJson = XML.toJSONObject(xml);
   lom = new LOMRoot(lomJson);
   Assert.assertNotNull(lom.getGeneral());
   Assert.assertNotNull(lom.getLifeCycle());
   Assert.assertNotNull(lom.getMetaMetadata());
   Assert.assertNotNull(lom.getTechnical());
   Assert.assertNotNull(lom.getEducational());
   Assert.assertNotNull(lom.getRights());
   Assert.assertNotNull(lom.getRelation());
   Assert.assertNotNull(lom.getAnnotation());
   Assert.assertNotNull(lom.getClassification());
   
   Assert.assertNotNull(lom.getGeneral().getAggregationLevel());
   Assert.assertNotNull(lom.getGeneral().getDescription().get(0).getLangString().getLanguage());
   Assert.assertNotNull(lom.getGeneral().getDescription().get(1).getLangString().getString());
   Assert.assertNotNull(lom.getGeneral().getCoverage().get(0).getLangString().getLanguage());
   Assert.assertNotNull(lom.getGeneral().getCoverage().get(1).getLangString().getString());
   Assert.assertNotNull(lom.getGeneral().getIdentifier().get(0).getEntry());
   Assert.assertNotNull(lom.getGeneral().getIdentifier().get(0).getCatalog());
   Assert.assertNotNull(lom.getGeneral().getKeyword().get(0).getLangString().getLanguage());
   Assert.assertNotNull(lom.getGeneral().getKeyword().get(1).getLangString().getString());
   Assert.assertNotNull(lom.getGeneral().getLanguage().get(1));
   Assert.assertNotNull(lom.getGeneral().getStructure().getSource());
   Assert.assertNotNull(lom.getGeneral().getStructure().getValue());
   Assert.assertNotNull(lom.getGeneral().getTitle().getLangString().getLanguage());
   Assert.assertNotNull(lom.getGeneral().getTitle().getLangString().getString());
   
   Assert.assertNotNull(lom.getLifeCycle().getContribute());
   Assert.assertNotNull(lom.getLifeCycle().getStatus().getSource());
   Assert.assertNotNull(lom.getLifeCycle().getStatus().getValue());
   Assert.assertNotNull(lom.getLifeCycle().getVersion().getLangString().getLanguage());
   Assert.assertNotNull(lom.getLifeCycle().getVersion().getLangString().getString());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getDate().getDateTime());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getDate().getDescription());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getEntity().get(1).getAddress());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getEntity().get(1).getContent());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getEntity().get(1).getEmail());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getEntity().get(1).getName());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getEntity().get(1).getOrganization());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getRole().getValue());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getRole().getSource());
   Assert.assertNotNull(lom.getLifeCycle().getContribute().get(0).getType());
   
   Assert.assertNotNull(lom.getMetaMetadata().getLanguage());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getDate().getDateTime());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getDate().getDescription());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getEntity().get(1).getOrganization());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getRole().getSource());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getRole().getValue());
   Assert.assertNotNull(lom.getMetaMetadata().getContribute().get(0).getType());
   Assert.assertNotNull(lom.getMetaMetadata().getMetadataSchema().get(1));
   Assert.assertNotNull(lom.getMetaMetadata().getIdentifier().get(0).getCatalog());
   Assert.assertNotNull(lom.getMetaMetadata().getIdentifier().get(0).getEntry());
   
   Assert.assertNotNull(lom.getTechnical().getSize());
   Assert.assertNotNull(lom.getTechnical().getDuration().getDuration());
   Assert.assertNotNull(lom.getTechnical().getDuration().getDescription());
   Assert.assertNotNull(lom.getTechnical().getFormat().get(0));
   Assert.assertNotNull(lom.getTechnical().getInstallationRemarks().getLangString().getString());
   Assert.assertNotNull(lom.getTechnical().getLocation().get(0));
   Assert.assertNotNull(lom.getTechnical().getOtherPlatFormRequirements().getLangString().getString());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(1));
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getMinimumVersion());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getName().getValue());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getName().getSource());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getType().getSource());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getType().getValue());
   Assert.assertNotNull(lom.getTechnical().getRequirement().get(0).getOrComposite().get(0).getMaxmumVersion());
   
   Assert.assertNotNull(lom.getEducational().get(0).getContext().get(0).getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getContext().get(0).getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getDescription().get(0).getLangString().getString());
   Assert.assertNotNull(lom.getEducational().get(0).getDifficulty().getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getDifficulty().getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getIntendedEndUserRole().get(0).getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getIntendedEndUserRole().get(0).getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getIntendedEndUserRole().get(1));
   Assert.assertNotNull(lom.getEducational().get(0).getInteractivityLevel().getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getInteractivityLevel().getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getInteractivityType().getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getInteractivityType().getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getLanguage().get(0));
   Assert.assertNotNull(lom.getEducational().get(0).getLanguage().get(1));
   Assert.assertNotNull(lom.getEducational().get(0).getLearningResourceType().get(0).getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getLearningResourceType().get(0).getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getLearningResourceType().get(1));
   Assert.assertNotNull(lom.getEducational().get(0).getSemanticDensity().getSource());
   Assert.assertNotNull(lom.getEducational().get(0).getSemanticDensity().getValue());
   Assert.assertNotNull(lom.getEducational().get(0).getTypicalAgeRange().get(1).getLangString().getString());
   Assert.assertNotNull(lom.getEducational().get(0).getTypicalLearningTime().getDuration());
   Assert.assertNotNull(lom.getEducational().get(0).getTypicalLearningTime().getDescription());
   
   Assert.assertNotNull(lom.getRights().getCopyrightAndOtherRestrictions().getSource());
   Assert.assertNotNull(lom.getRights().getCopyrightAndOtherRestrictions().getValue());
   Assert.assertNotNull(lom.getRights().getCost().getSource());
   Assert.assertNotNull(lom.getRights().getCost().getValue());
   Assert.assertNotNull(lom.getRights().getDescription().getLangString().getString());
   
   Assert.assertNotNull(lom.getRelation().get(0).getKind().getSource());
   Assert.assertNotNull(lom.getRelation().get(0).getKind().getValue());
   Assert.assertNotNull(lom.getRelation().get(0).getResource().getIdentifier());
   Assert.assertNotNull(lom.getRelation().get(0).getResource().getDescription());
   
   Assert.assertNotNull(lom.getAnnotation().get(0).getEntity());
   Assert.assertNotNull(lom.getAnnotation().get(0).getDate().getDateTime());
   Assert.assertNotNull(lom.getAnnotation().get(0).getDate().getDescription().getLangString().getString());
   Assert.assertNotNull(lom.getAnnotation().get(0).getDescription().getLangString().getString());
   
   Assert.assertNotNull(lom.getClassification().get(0).getTaxonPath().get(1).getSource().getLangString().getString());
   Assert.assertNotNull(lom.getClassification().get(0).getTaxonPath().get(1).getTaxon().get(1).getId());
   Assert.assertNotNull(lom.getClassification().get(0).getTaxonPath().get(1).getTaxon().get(1).getEntry().getLangString().getString());
   Assert.assertNotNull(lom.getClassification().get(0).getDescription().getLangString().getString());
   Assert.assertNotNull(lom.getClassification().get(0).getPurpose());
   Assert.assertNotNull(lom.getClassification().get(0).getKeyword().get(1).getLangString().getString());
  }
}

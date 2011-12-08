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

import junit.framework.Assert;

import org.junit.Test;

public class ManifestTest {
  public String xmlcontent = "<manifest identifier=\"ID000000\" version=\"String\" xml:base=\"http://www.altova.com\"> <metadata> <schema>String</schema> " +
  		"<schemaversion>String</schemaversion><lom><general><language>en</language></general></lom></metadata> <organizations default=\"ID000000\">" +
  		" <organization structure=\"hierarchical\" identifier=\"ID000001\"> " +
  		"<title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000002\"> <title>String</title> " +
  		"<item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000003\"> <title>String</title> <metadata><schema>String</schema></metadata> " +
  		"</item> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000004\"> <title>String</title> <metadata /> </item> <metadata /> </item> " +
  		"<metadata /> </organization> <organization structure=\"hierarchical\" identifier=\"ID000005\"> <title>String</title> <item isvisible=\"true\" parameters=\"String\" " +
  		"identifierref=\"String\" identifier=\"ID000006\"> <title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000007\"> " +
  		"<title>String</title> <metadata /> </item> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000008\"> <title>String</title> " +
  		"<metadata /> </item> <metadata /> </item> <metadata /> </organization> </organizations> <resources xml:base=\"http://www.altova.com\"> " +
  		"<resource identifier=\"ID000009\" type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> <metadata /> <file href=\"http://www.altova.com\"> " +
  		"<metadata /> </file> <file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> </resource> <resource identifier=\"ID000010\" " +
  		"type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> <metadata /> <file href=\"http://www.altova.com\"> <metadata /> </file> " +
  		"<file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> </resource> </resources> " +
  		"<manifest identifier=\"ID000011\" version=\"String\" xml:base=\"http://www.altova.com\"> <metadata> <schema>String</schema> <schemaversion>String</schemaversion> " +
  		"</metadata> <organizations default=\"ID000011\"> <organization structure=\"hierarchical\" identifier=\"ID000012\"> <title>String</title> <item isvisible=\"true\"" +
  		" parameters=\"String\" identifierref=\"String\" identifier=\"ID000013\"> <title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" " +
  		"identifier=\"ID000014\"> <title>String</title> <metadata /> </item> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000015\"> " +
  		"<title>String</title> <metadata /> </item> <metadata /> </item> <metadata /> </organization> <organization structure=\"hierarchical\" identifier=\"ID000016\"> " +
  		"<title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000017\"> <title>String</title> <item isvisible=\"true\" " +
  		"parameters=\"String\" identifierref=\"String\" identifier=\"ID000018\"> <title>String</title> <metadata /> </item> <item isvisible=\"true\" parameters=\"String\" " +
  		"identifierref=\"String\" identifier=\"ID000019\"> <title>String</title> <metadata /> </item> <metadata /> </item> <metadata /> </organization> </organizations> " +
  		"<resources xml:base=\"http://www.altova.com\"> <resource identifier=\"ID000020\" type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> " +
  		"<metadata /> <file href=\"http://www.altova.com\"> <metadata /> </file> <file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> " +
  		"</resource> <resource identifier=\"ID000021\" type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> <metadata /> " +
  		"<file href=\"http://www.altova.com\"> <metadata /> </file> <file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> </resource> " +
  		"</resources> </manifest> <manifest identifier=\"ID000022\" version=\"String\" xml:base=\"http://www.altova.com\"> <metadata> <schema>String</schema> " +
  		"<schemaversion>String</schemaversion> </metadata> <organizations default=\"ID000022\"> <organization structure=\"hierarchical\" identifier=\"ID000023\"> " +
  		"<title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000024\"> <title>String</title> " +
  		"<item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000025\"> <title>String</title> <metadata /> </item> <item isvisible=\"true\" " +
  		"parameters=\"String\" identifierref=\"String\" identifier=\"ID000026\"> <title>String</title> <metadata /> </item> <metadata /> </item> <metadata /> </organization> " +
  		"<organization structure=\"hierarchical\" identifier=\"ID000027\"> <title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" " +
  		"identifier=\"ID000028\"> <title>String</title> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000029\"> " +
  		"<title>String</title> <metadata /> </item> <item isvisible=\"true\" parameters=\"String\" identifierref=\"String\" identifier=\"ID000030\"> " +
  		"<title>String</title> <metadata /> </item> <metadata /> </item> <metadata /> </organization> </organizations> <resources xml:base=\"http://www.altova.com\"> " +
  		"<resource identifier=\"ID000031\" type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> <metadata /> <file href=\"http://www.altova.com\"> " +
  		"<metadata /> </file> <file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> </resource> <resource identifier=\"ID000032\" " +
  		"type=\"String\" xml:base=\"http://www.altova.com\" href=\"http://www.altova.com\"> <metadata /> <file href=\"http://www.altova.com\"> <metadata /> </file> " +
  		"<file href=\"http://www.altova.com\"> <metadata /> </file> <dependency identifierref=\"String\" /> </resource> </resources> </manifest> </manifest>"; 
  public ManifestTest() {
    
  }
  
  @Test
  public void test() throws Exception{
    Manifest manifest = new Manifest(xmlcontent);
    String content = manifest.generateXML();
    manifest = new Manifest(content);
    Assert.assertNotNull(manifest.getIdentifier());
    Assert.assertNotNull(manifest.getVersion());
    Assert.assertNotNull(manifest.getXmlBase());
    
    Assert.assertNotNull(manifest.getMetadata().getSchema());
    Assert.assertNotNull(manifest.getMetadata().getSchemaVersion());
    Assert.assertNotNull(manifest.getMetadata().getLom());
    Assert.assertNotNull(manifest.getOrganizations().getDefaultID());
    Assert.assertNotNull(manifest.getOrganizations().searchItem("ID000004").getTitle());
    Assert.assertNotNull(manifest.getOrganizations().getOrganization("ID000001").getItems().get(0).getItems().get(1).getIdentifier());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).searchSubItem("ID000004").getIsvisible());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).getStructure());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).getTitle());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).getIdentifier());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).getItems().get(0).getItems().get(1).getIdentifierRef());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(0).getItems().get(0).getItems().get(1).getParameters());
    Assert.assertNotNull(manifest.getOrganizations().getOrganizations().get(1).getItems().get(0).getTitle());
    Assert.assertNotNull(manifest.getOrganizations().searchItem("ID000003").getMetadata().getSchema());
    
    Assert.assertNotNull(manifest.getResources().getXmlBase());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getHref());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getIdentifier());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getType());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getXmlBase());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getFiles().get(1).getHref());
    Assert.assertNotNull(manifest.getResources().getResources().get(1).getDependencies().get(0).getIdentifierRef());
    Assert.assertNotNull(manifest.getSubManifests().get(1).getIdentifier());
  }
}

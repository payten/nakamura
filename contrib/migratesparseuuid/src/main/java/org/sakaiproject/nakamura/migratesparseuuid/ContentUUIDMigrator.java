/**
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
package org.sakaiproject.nakamura.migratesparseuuid;

import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true,immediate=true)
@Service(value=PropertyMigrator.class)
public class ContentUUIDMigrator implements PropertyMigrator {

	private static final String DEFAULT_ORIGINAL_FIELD = "_id";
	@Property(value=DEFAULT_ORIGINAL_FIELD)
	private static final String PROP_ORIGINAL_FIELD = "migrateuuid.original.field";

	
	private String originalField;


	@Activate
	public void activate(Map<String,Object> props){
		originalField = PropertiesUtil.toString(PROP_ORIGINAL_FIELD, DEFAULT_ORIGINAL_FIELD);
	}
	

	public boolean migrate(String rid, Map<String, Object> properties) {
		if (originalField.equals(Content.getUuidField())){
			return false;
		}
        String theValue = (String)properties.get(originalField);
        if (theValue != null ){
        	properties.put(Content.getUuidField(), theValue);
        	properties.remove(originalField);
        	return true;
        }
		return false;
	}

}

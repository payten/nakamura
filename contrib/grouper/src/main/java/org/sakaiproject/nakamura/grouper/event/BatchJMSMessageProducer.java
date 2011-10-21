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
package org.sakaiproject.nakamura.grouper.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Post {@link Message}s to a {@link Queue} that cause the current groups,
 * courses, and contacts are sent to Grouper.
 */
@Service
@Component(immediate = true, metatype=true)
public class BatchJMSMessageProducer implements BatchOperationsManager {

	private static Logger log = LoggerFactory.getLogger(BatchJMSMessageProducer.class);

	public static final String QUEUE_NAME = "org/sakaiproject/nakamura/grouper/batch";

	@Reference
	protected ConnectionFactoryService connFactoryService;

	@Reference
	protected GrouperConfiguration grouperConfiguration;

	@Reference
	protected SolrServerService solrServerService;

	@Reference
	protected Repository repository;

	protected boolean transacted = true;

	@Property(boolValue=false)
	protected static final String PROP_DO_BATCH_PROVISIONED_GROUPS = "grouper.dobatch.provisioned.groups";
	protected boolean doBatchProvisionedGroups;

	@Property(boolValue=false)
	protected static final String PROP_DO_BATCH_ADHOC_GROUPS = "grouper.dobatch.adhoc.groups";
	protected boolean doBatchAdhocGroups;

	@Property(boolValue=false)
	protected static final String PROP_DO_BATCH_CONTACTS = "grouper.dobatch.contacts";
	protected boolean doBatchContacts;

	protected static final String DEFAULT_ALL_GROUPS_QUERY = "(resourceType:authorizable AND type:g)";
	@Property(value=DEFAULT_ALL_GROUPS_QUERY)
	protected static final String PROP_ALL_GROUPS_QUERY = "grouper.query.groups.all";
	protected String allGroupsQuery;

	protected static final String DEFAULT_ALL_USERS_QUERY = "(resourceType:authorizable AND type:u)";
	@Property(value=DEFAULT_ALL_USERS_QUERY)
	protected static final String PROP_ALL_USERS_QUERY = "grouper.query.users.all";
	protected String allUsersQuery;

	protected static final int DEFAULT_QUERY_PAGE_SIZE = 100;
	@Property(intValue=DEFAULT_QUERY_PAGE_SIZE)
	protected static final String PROP_QUERY_PAGE_SIZE = "grouper.query.page.size";
	protected int queryPageSize;

	@Activate
	@Modified
	public void updated(Map<String,Object> props) throws Exception {
		doBatchProvisionedGroups = OsgiUtil.toBoolean(props.get(PROP_DO_BATCH_PROVISIONED_GROUPS), false);
		doBatchAdhocGroups = OsgiUtil.toBoolean(props.get(PROP_DO_BATCH_ADHOC_GROUPS), false);
		doBatchContacts = OsgiUtil.toBoolean(props.get(PROP_DO_BATCH_CONTACTS), false);
		allGroupsQuery = OsgiUtil.toString(props.get(PROP_ALL_GROUPS_QUERY), DEFAULT_ALL_GROUPS_QUERY);
		allUsersQuery = OsgiUtil.toString(props.get(PROP_ALL_USERS_QUERY), DEFAULT_ALL_USERS_QUERY);
		queryPageSize = OsgiUtil.toInteger(props.get(PROP_QUERY_PAGE_SIZE), DEFAULT_QUERY_PAGE_SIZE);

		if (doBatchProvisionedGroups){
			doProvisionedGroups();
		}
		if (doBatchAdhocGroups){
			doAdhocGroups();
		}
		if (doBatchContacts) {
			doContacts();
		}
	}


	public void doAdhocGroups() throws Exception {
		doGroups(false);
	}

	public void doProvisionedGroups() throws Exception{
		doGroups(true);
	}

	protected void doGroups(boolean doProvisionedGroups) throws Exception {

		org.sakaiproject.nakamura.api.lite.Session sparseSession = repository.loginAdministrative();
		AuthorizableManager authorizableManager = sparseSession.getAuthorizableManager();

		int start = 0;

		SolrServer server = solrServerService.getServer();
		SolrQuery query = new SolrQuery();
		query.setQuery(allGroupsQuery);
		query.setStart(start);
		query.setRows(queryPageSize);

		QueryResponse response = server.query(query);
	    long totalResults = response.getResults().getNumFound();

	    List<String> groupIds = new ArrayList<String>();
	    while (start < totalResults){
	        query.setStart(start);
	        groupIds.clear();
	        List<SolrDocument> resultDocs = response.getResults();
	        
	        for (SolrDocument doc : resultDocs){
	            String authorizableId = (String)doc.get("id");

	            // This is a HACK since I haven't figured out how to ask solr for the pseudo groups yet.
	            for (String suffix : grouperConfiguration.getPseudoGroupSuffixes()){
	            	String psgId = authorizableId + "-" + suffix;
	            	Group group = (Group)authorizableManager.findAuthorizable(psgId);
	            	if (group == null){
	            		continue;
	            	}

	            	boolean isProvisioned = false;
	            	if (group.getProperty("grouper:provisioned") != null){
	            		isProvisioned = Boolean.parseBoolean((String)group.getProperty("grouper:provisioned"));
	            	}

	            	if ( (doProvisionedGroups && isProvisioned) ||
	            			(doProvisionedGroups == false && isProvisioned == false)){
	            		groupIds.add(psgId);
	            		log.debug("Sending batch event for {}", authorizableId + suffix);
	            	}
		        }
	        }
 	       	sendGroupMessages(groupIds);
	        start += resultDocs.size();
	        log.debug("Found {} groups.", resultDocs.size());
	    }
	}

	public void doContacts() throws SolrServerException, JMSException {
		int start = 0;
		int items = 25;

		SolrServer server = solrServerService.getServer();
		SolrQuery query = new SolrQuery();
		query.setQuery(allUsersQuery);
		query.setStart(start);
		query.setRows(items);

		QueryResponse response = server.query(query);
	    long totalResults = response.getResults().getNumFound();

	    List<String> groupIds = new ArrayList<String>();
	    while (start < totalResults){
	        query.setStart(start);
	        groupIds.clear();
	        List<SolrDocument> resultDocs = response.getResults();
	        for (SolrDocument doc : resultDocs){
	        	groupIds.add("g-contacts-" + (String)doc.get("id"));
	        	log.debug("Syncing contacts for {}", doc.get("id"));
	        }
 	       	sendGroupMessages(groupIds);

	        start += resultDocs.size();
	        log.debug("Found {} users.", resultDocs.size());
	    }
	}

	public void doOneGroup(String groupId) throws JMSException{
		sendGroupMessages(ImmutableList.of(groupId));
	}

	/**
	 * Send messages that trigger a batch update
	 * @param groupIds
	 * @throws JMSException
	 */
	private void sendGroupMessages(List<String> groupIds) throws JMSException {
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost:61616");
		ActiveMQConnection connection = (ActiveMQConnection) connectionFactory.createConnection();

		// http://activemq.apache.org/redelivery-policy.html
		// Configure the Queue to redeliver the message
		RedeliveryPolicy policy = connection.getRedeliveryPolicy();
		// Try .5 seconds later
		policy.setInitialRedeliveryDelay(500);
		// Increase the delay each time we fail to process the message
		policy.setBackOffMultiplier(grouperConfiguration.getJmsBackoffMultiplier());
		policy.setUseExponentialBackOff(true);
		policy.setMaximumRedeliveries(grouperConfiguration.getBatchMaxMessageRetries());
		connection.setRedeliveryPolicy(policy);

		Session session = connection.createSession(transacted, -1);
		Queue squeue = session.createQueue(QUEUE_NAME);
		MessageProducer producer = session.createProducer(squeue);

		for (String groupId : groupIds){
			Message msg = session.createMessage();
			msg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
			msg.setJMSType(QUEUE_NAME);
			msg.setStringProperty(Authorizable.ID_FIELD, groupId);
			producer.send(msg);
			session.commit();
			log.info("Sent: {} {} : messageId {}", new Object[] { QUEUE_NAME, groupId, msg.getJMSMessageID()});
			log.trace("{} : {}", msg.getJMSMessageID(), msg);
		}

		try {
			session.close();
		}
		finally {
			session = null;
		}
		try {
			connection.close();
		}
		finally {
			connection = null;
		}
	}
}

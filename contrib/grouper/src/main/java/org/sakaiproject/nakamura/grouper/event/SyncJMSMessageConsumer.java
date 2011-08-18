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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.name.ContactsGrouperNameProviderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SyncJMSMessageConsumer implements MessageListener {

	private static Logger log = LoggerFactory.getLogger(SyncJMSMessageConsumer.class);

	@Reference
	protected ConnectionFactoryService connFactoryService;

	@Reference
	protected GrouperManager grouperManager;

	@Reference
	protected GrouperConfiguration config;

	@Reference
	protected Repository repository;

	private Connection connection;
	private Session jmsSession;
	private MessageConsumer consumer;

	@Activate
	public void activate(Map<?,?> props){
		try {
 			if (connection == null){
				connection = connFactoryService.getDefaultPooledConnectionFactory().createConnection();
				jmsSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
				Destination destination = jmsSession.createQueue(SyncJMSMessageProducer.QUEUE_NAME);
				consumer = jmsSession.createConsumer(destination);
				consumer.setMessageListener(this);
				connection.start();
			}

		} catch (Exception e) {
			throw new RuntimeException (e);
		}
	}

	@Deactivate
	public void deactivate(){
		try {
			consumer.setMessageListener(null);
		}
		catch (JMSException jmse){
			log.error("Problem clearing the MessageListener.");
		}
		try {
			jmsSession.close();
		}
		catch (JMSException jmse){
			log.error("Problem closing JMS Session.");
		}
		finally {
			jmsSession = null;
		}

		try {
			connection.close();
		}
		catch (JMSException jmse){
			log.error("Problem closing JMS Connection.");
		}
		finally {
			connection = null;
		}
	}

	@SuppressWarnings("unchecked")
	public void onMessage(Message message){
		log.debug("Receiving a message on {} : {}", SyncJMSMessageProducer.QUEUE_NAME, message);
		try {

			String topic = message.getJMSType();
			String groupId = message.getStringProperty("path");

			StringBuilder operation = new StringBuilder();

			// A group was DELETED
			if ("org/sakaiproject/nakamura/lite/authorizables/DELETE".equals(topic)
					&& config.getDeletesEnabled()){
				Map<String, Object> attributes = (Map<String,Object>)message.getObjectProperty(StoreListener.BEFORE_EVENT_PROPERTY);
				doDeleteGroup(groupId, attributes);
				operation.append("DELETED ");
			}

			// A new group was ADDED or an existing group was UPDATED
			if ("org/sakaiproject/nakamura/lite/authorizables/ADDED".equals(topic)
					|| "org/sakaiproject/nakamura/lite/authorizables/UPDATED".equals(topic) ){

				org.sakaiproject.nakamura.api.lite.Session repositorySession = repository.loginAdministrative();
				AuthorizableManager am = repositorySession.getAuthorizableManager();
				Group group = (Group) am.findAuthorizable(groupId);

				boolean isProvisioned = group.getProperty("grouper:provisioned") != null;

				// These events should be under org/sakaiproject/nakamura/lite/authorizables/UPDATED
				// http://jira.sakaiproject.org/browse/KERN-1795
				String membersAdded = message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP);
				if (membersAdded != null){
					doAddMemberships(group, Arrays.asList(StringUtils.split(membersAdded, ",")));
					operation.append("ADD_MEMBERS ");
				}

				String membersRemoved = message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP);
				if (membersRemoved != null){
					doRemoveMemberships(group, Arrays.asList(StringUtils.split(membersRemoved, ",")));
					operation.append("REMOVE_MEMBERS ");
				}

				if (membersAdded == null && membersRemoved == null) {
					if (groupId.startsWith(ContactsGrouperNameProviderImpl.CONTACTS_GROUPID_PREFIX)){
						operation.append("UPDATE CONTACTS ");
						doCreateGroup(group);
					} else if (!isProvisioned){
						operation.append("CREATE ");
						doCreateGroup(group);
					}

				}
				repositorySession.logout();
			}

			// The message was processed successfully. No exceptions were thrown.
			// We acknowledge the message and its removed from the queue
			message.acknowledge();

			// We got a message that we didn't know what to do with.
			if (operation.toString().equals("")){
				log.error("I don't know what to do with this topic: {}. Turn on debug logs to see the message.", topic);
				log.debug(message.toString());
			} else {
				log.info("Successfully processed and acknowledged. {}, {}", operation.toString(), groupId);
				log.debug(message.toString());
			}
		}
		catch (JMSException jmse){
			log.error("JMSException while processing message.", jmse);
		}
		catch (Exception e){
			log.error("Exception while processing message.", e);
		}
	}

	private void doCreateGroup(Group group) throws GrouperException{
		grouperManager.createGroup(group.getId());
		grouperManager.addMemberships(group.getId(), Arrays.asList(group.getMembers()));
	}

	private void doDeleteGroup(String groupId, Map<String,Object> attributes) throws GrouperException{
		String grouperName = (String)attributes.get("grouper:name");
		if (grouperName != null) {
			grouperManager.deleteGroup(groupId, attributes);
		}
	}

	private void doAddMemberships(Group group, List<String> membersAdded) throws GrouperException{
		grouperManager.createGroup(group.getId());
		grouperManager.addMemberships(group.getId(), membersAdded);
	}

	private void doRemoveMemberships(Group group, List<String> membersRemoved) throws GrouperException{
		grouperManager.removeMemberships(group.getId(), membersRemoved);
	}
}

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
import java.util.Arrays;
import java.util.Date;
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
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.exception.InvalidGroupIdException;
import org.sakaiproject.nakamura.grouper.util.GroupUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@Component
public class SyncJMSMessageConsumer extends AbstractConsumer implements MessageListener {

	private static Logger log = LoggerFactory.getLogger(SyncJMSMessageConsumer.class);

	private static final String SLOW_MESSAGE_EMAIL_SUBJECT = "Grouper Slow Sync Message Notification";

	public static final String OP_GROUP_CREATED = "GROUP_CREATE";
	public static final String OP_CONTACTS_GROUP_CREATED = "GROUP_CONTACTS_CREATED";
	public static final String OP_GROUP_DELETED = "GROUP_DELETE";
	public static final String OP_MEMBERSHIP_ADDED = "MEMBERSHIP_ADDED";
	public static final String OP_MEMBERSHIP_REMOVED = "MEMBERSHIP_REMOVED";

	@Reference
	protected ConnectionFactoryService connFactoryService;

	@Reference
	protected Repository repository;

	@Reference
	protected LiteMessagingService messagingService;

	private Connection connection;
	private Session jmsSession;
	private MessageConsumer consumer;
	private boolean transacted = true;

	// The timestamp the last time we sent an email about an undelivered message
	private long lastSlowMessageEmail = 0;

	// How often to send a slow message email
	// 20 minutes
	private long slowEmailInterval = 1000 * 60 * 20;
	// How long to wait until a message is considered slow
	// 4 hours

	@Activate
	public void activate(Map<?,?> props){
		try {
 			if (connection == null){
				connection = connFactoryService.getDefaultPooledConnectionFactory().createConnection();
				jmsSession = connection.createSession(transacted, -1);
				Destination destination = jmsSession.createQueue(SyncJMSMessageProducer.QUEUE_NAME);
				consumer = jmsSession.createConsumer(destination);
				consumer.setMessageListener(this);
				connection.start();
				log.info("Listening on queue : {}", SyncJMSMessageProducer.QUEUE_NAME);
			}
		} catch (Exception e) {
			log.error("Unable to register a listener on {}. {}",
					SyncJMSMessageProducer.QUEUE_NAME, e.getMessage());
			throw new RuntimeException (e);
		}
	}

	@Deactivate
	public void deactivate(){
		try {
			consumer.setMessageListener(null);
		}
		catch (JMSException jmse){
			log.error("Problem clearing the MessageListener.", jmse);
		}
		try {
			jmsSession.close();
		}
		catch (JMSException jmse){
			log.error("Problem closing JMS Session.", jmse);
		}
		finally {
			jmsSession = null;
		}

		try {
			connection.close();
		}
		catch (JMSException jmse){
			log.error("Problem closing JMS Connection.", jmse);
		}
		finally {
			connection = null;
		}
	}

	public void onMessage(Message message){

		try {
			handleIfMessageIsOld(message);

			String topic = message.getJMSType();
			String groupId = message.getStringProperty(StoreListener.PATH_PROPERTY);

			log.info("Received: {} {} : messageId {}", new Object[] { topic, groupId, message.getJMSMessageID()});
			log.trace(message.toString());

			// Keep track of what happened as a result of this message.
			List<String> operation = new ArrayList<String>();

			// A group was DELETED
			if ("org/sakaiproject/nakamura/lite/authorizables/DELETE".equals(topic)){
				onGroupDelete(message);
				operation.add(OP_GROUP_DELETED);
			}
			// A new group was ADDED or an existing group was UPDATED
			else if ("org/sakaiproject/nakamura/lite/authorizables/ADDED".equals(topic)
					|| "org/sakaiproject/nakamura/lite/authorizables/UPDATED".equals(topic) ){
				if (onMembershipAdded(message)){
					operation.add(OP_MEMBERSHIP_ADDED);
				}
				if (onMembershipRemoved(message)){
					operation.add(OP_MEMBERSHIP_ADDED);
				}
				// No members added or removed
				if (operation.size() == 0){
					operation.add(onGroupAdd(message));
				}
			}

			// The message was processed successfully. No exceptions were thrown.
			jmsSession.commit();

			// We got a message that we didn't know what to do with.
			if (operation.toString().equals("")){
				log.error("I don't know what to do with this topic: {}. Turn on debug logs to see the message.", topic);
				log.debug(message.toString());
			} else {
				log.info("Successfully processed and acknowledged. {}, {}", StringUtils.join(operation, ","), groupId);
			}
		}
		catch (JMSException jmse){
			log.error("JMSException while processing message.", jmse);
		}
		catch (Exception e){
			log.error("Exception while processing message.", e);
			try {
				jmsSession.rollback();
			} catch (JMSException e1) {
				log.error("Unable to rollback JMS session.");
			}
		}
	}

	/**
	 * Create a group in Grouper.
	 * @param message
	 * @return a string descibing the action taken.
	 * @throws JMSException
	 * @throws ClientPoolException
	 * @throws StorageClientException
	 * @throws AccessDeniedException
	 * @throws GrouperException
	 */
	private String onGroupAdd(Message message)
	throws JMSException, ClientPoolException, StorageClientException, AccessDeniedException, GrouperException {

		String groupId = message.getStringProperty(StoreListener.PATH_PROPERTY);
		String grouperName = grouperNameManager.getGrouperName(groupId);
		String membersAdded = message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP);
		String membersRemoved = message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP);
		String operation = null;

		if (membersAdded == null && membersRemoved == null) {
			log.debug("messageId {} : create or update {}", new String[]{ message.getJMSMessageID(), grouperName});

			org.sakaiproject.nakamura.api.lite.Session sparseSession = repository.loginAdministrative();
			AuthorizableManager am = sparseSession.getAuthorizableManager();
			Group group = (Group) am.findAuthorizable(groupId);

			if (GroupUtil.isContactsGroup(groupId)){
				operation = OP_CONTACTS_GROUP_CREATED;
				doCreateGroup(group, grouperName, sparseSession);
			} else {
				operation = OP_GROUP_CREATED;
				doCreateGroup(group, grouperName, sparseSession);
				syncMemberships(group, grouperName, sparseSession);
			}
			sparseSession.logout();
		}
		return operation;
	}

	/**
	 * Delete a group in Grouper.
	 * @param msg
	 * @throws JMSException
	 * @throws GrouperException
	 */
	@SuppressWarnings("unchecked")
	private void onGroupDelete(Message msg) throws JMSException, GrouperException{

		if (!config.getDeletesEnabled()){
			return;
		}
		Map<String, Object> attributes = (Map<String,Object>)msg.getObjectProperty(StoreListener.BEFORE_EVENT_PROPERTY);
		String grouperName = (String)attributes.get(GrouperManager.GROUPER_NAME_PROP);
		if (grouperName == null) {
			throw new InvalidGroupIdException("No grouper name  in the pre-delete properties");
		}
		grouperManager.deleteGroup(grouperName);
	}

	/**
	 * If this message indicates members were added to a group send that information to Grouper.
	 * @param message the message that triggered this consumer
	 * @return if members were added to a group as a result of this message.
	 * @throws JMSException
	 * @throws ClientPoolException
	 * @throws StorageClientException
	 * @throws AccessDeniedException
	 * @throws GrouperException
	 */
	private boolean onMembershipAdded(Message message)
	throws JMSException, ClientPoolException, StorageClientException, AccessDeniedException, GrouperException{
		String groupId = message.getStringProperty(StoreListener.PATH_PROPERTY);
		String membersAdded = message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP);
		String grouperName = grouperNameManager.getGrouperName(groupId);
		boolean handled = false;

		if (membersAdded != null){
			org.sakaiproject.nakamura.api.lite.Session sparseSession = repository.loginAdministrative();
			AuthorizableManager am = sparseSession.getAuthorizableManager();
			Group group = (Group) am.findAuthorizable(groupId);

			if (group != null){
				log.debug("messageId {} : add members to {},  members={}",
						new String[] { message.getJMSMessageID(), grouperName, membersAdded });
				List<String> membersToAdd = Arrays.asList(StringUtils.split(membersAdded, ","));
				doAddMemberships(group, grouperName, membersToAdd, sparseSession);
			}
			sparseSession.logout();
		}
		return handled;
	}

	/**
	 * If this message indicates members were removed from a group send that information to Grouper.
	 * @param message the message that triggered this consumer
	 * @return if members were removed from a group as a result of this message.
	 * @throws JMSException
	 * @throws ClientPoolException
	 * @throws StorageClientException
	 * @throws AccessDeniedException
	 * @throws GrouperException
	 */
	private boolean onMembershipRemoved(Message message)
	throws JMSException, ClientPoolException, StorageClientException, AccessDeniedException, GrouperException {
		String groupId = message.getStringProperty(StoreListener.PATH_PROPERTY);
		String membersRemoved = message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP);
		String grouperName = grouperNameManager.getGrouperName(groupId);
		boolean handled = false;

		if (membersRemoved != null){
			org.sakaiproject.nakamura.api.lite.Session sparseSession = repository.loginAdministrative();
			AuthorizableManager am = sparseSession.getAuthorizableManager();
			Group group = (Group) am.findAuthorizable(groupId);

			if (group != null){
				log.debug("messageId {} : remove members to {},  members={}",
						new String[] { message.getJMSMessageID(), grouperName, membersRemoved });
				List<String> membersToRemove = Arrays.asList(StringUtils.split(membersRemoved, ","));
				doRemoveMemberships(group, grouperName, membersToRemove, sparseSession);
				handled = true;
			}
			sparseSession.logout();
		}
		return handled;
	}

	// --- Email notifications for old messages

	/**
	 * Send an email if the current message is older than a configurable threshold
	 * and its been at least 10 minutes since we sent a email.
	 * @param message
	 * @throws JMSException
	 */
	private void handleIfMessageIsOld(Message message) throws JMSException {
		long timeQueued = message.getJMSTimestamp();
		long now = new Date().getTime();

		try {
			if ( // it took too long
				((now - timeQueued) > config.getSlowMessageThreshold())
				// and its been long enough since we sent the last email
				&& (now - lastSlowMessageEmail) > slowEmailInterval
				){
				sendSlowMessageEmail(message);
				lastSlowMessageEmail = now;
			}
		} catch (Exception e) {
			log.error("There was an error sending the notification email.", e);
		}
	}

	/**
	 * Send the email about the slow message.
	 * @param message the message that is taking too long to  be delivered.
	 * @throws ClientPoolException
	 * @throws StorageClientException
	 * @throws AccessDeniedException
	 * @throws JMSException
	 */
	private void sendSlowMessageEmail(Message message) throws ClientPoolException, StorageClientException, AccessDeniedException, JMSException {

		org.sakaiproject.nakamura.api.lite.Session sparseSession = repository.loginAdministrative();

		// Inspired by /dev/lib/sakai/sakai.api.communication.js
		Builder<String,Object> msg = new ImmutableMap.Builder<String, Object>();
		msg.put(MessageConstants.PROP_SAKAI_TYPE, MessageConstants.TYPE_SMTP);
		msg.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, MessageConstants.SAKAI_MESSAGE_RT);
		msg.put(MessageConstants.PROP_SAKAI_SENDSTATE, "pending");
		msg.put(MessageConstants.PROP_SAKAI_MESSAGEBOX, MessageConstants.STATE_PENDING);
		msg.put(MessageConstants.PROP_SAKAI_TO, config.getNotificationRecipient());
		msg.put(MessageConstants.PROP_SAKAI_FROM, sparseSession.getUserId());
		msg.put(MessageConstants.PROP_SAKAI_SUBJECT, SLOW_MESSAGE_EMAIL_SUBJECT);
		msg.put(MessageConstants.PROP_SAKAI_BODY, "This is the body");
		msg.put(UserConstants.SAKAI_CATEGORY, "message");
		msg.put("_charset_", "UTF-8");
		msg.put(MessageConstants.PROP_TEMPLATE_PATH, "/var/grouper/email/slow-message-notification");
		msg.put(MessageConstants.PROP_TEMPLATE_PARAMS, "msgId=" + message.getJMSMessageID() + "|msg=" + message.toString());

		messagingService.create(sparseSession, msg.build());
	}
}

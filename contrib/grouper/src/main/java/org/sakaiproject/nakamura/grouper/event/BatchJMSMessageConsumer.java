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

import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The batch process sends all of the current group information in SakaiOAE to
 * Grouper. The {@link BatchJMSMessageProducer} posts a {@link Message} for each
 * group its able to find. This class uses a {@link GrouperManager} to do the
 * work.
 */
@Component
public class BatchJMSMessageConsumer extends AbstractConsumer implements MessageListener {

	private static Logger log = LoggerFactory.getLogger(BatchJMSMessageConsumer.class);

	@Reference
	protected ConnectionFactoryService connFactoryService;

	@Reference
	protected Repository repository;

	private Connection connection;
	private Session session;
	private MessageConsumer consumer;
	private boolean transacted = true;

	@Activate
	public void activate(Map<?,?> props){
		try {
			if (connection == null){
				connection = connFactoryService.getDefaultPooledConnectionFactory().createConnection();
				session = connection.createSession(transacted, -1);
				Destination destination = session.createQueue(BatchJMSMessageProducer.QUEUE_NAME);
				consumer = session.createConsumer(destination);
				consumer.setMessageListener(this);
				connection.start();
				log.info("Listening on queue : {}", BatchJMSMessageProducer.QUEUE_NAME);
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
			session.close();
		}
		catch (JMSException jmse){
			log.error("Problem closing JMS Session.");
		}
		finally {
			session = null;
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

	@Override
	public void onMessage(Message message){
		try {
			log.info("Received: messageId {}", message.getJMSMessageID());
			log.debug(message.toString());

			String groupId = message.getStringProperty(Authorizable.ID_FIELD);
			org.sakaiproject.nakamura.api.lite.Session repositorySession = repository.loginAdministrative();
			Authorizable group = repositorySession.getAuthorizableManager().findAuthorizable(groupId);

			if (group != null && group.isGroup()) {
				String grouperName = grouperNameManager.getGrouperName(groupId);
				doCreateGroup((Group)group, grouperName, repositorySession);
				syncMemberships((Group)group, grouperName, repositorySession);
			}
			else {
				if (group == null){
					log.error("{} could not be found.", groupId);
				}
				else if (group.isGroup() == false){
					log.error("{} is not a group", groupId);
				}
			}
			session.commit();
			log.info("Successfully processed and acknowledged. {}, {}", message.getJMSMessageID(), groupId);
			repositorySession.logout();
		} catch (Exception e) {
			try {
				session.rollback();
			} catch (JMSException e1) {
				log.error("Unable to roll back the JMS session.", e);
			}
			log.error("Error processing the message.", e);
		}
	}
}

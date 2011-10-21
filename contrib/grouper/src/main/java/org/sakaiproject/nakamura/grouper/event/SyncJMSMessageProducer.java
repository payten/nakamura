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
import java.util.regex.Pattern;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture {@link Authorizable} events and put them on a special Queue to be processed.
 *
 * When Groups are created or updated we are notified of an {@link Event} via the OSGi
 * {@link EventAdmin} service. We then create a {@link Message} and place it on a {@link Queue}.
 *
 * The {@link GrouperJMSMessageConsumer} will receive those messages and acknowledge them as they
 * are successfully processed.
 */
@Service
@Component(immediate = true, metatype=true)
@Properties(value = {
		@Property(name = EventConstants.EVENT_TOPIC,
				value = {
				"org/sakaiproject/nakamura/lite/authorizables/ADDED",
				"org/sakaiproject/nakamura/lite/authorizables/UPDATED",
				"org/sakaiproject/nakamura/lite/authorizables/DELETE"
		})
})
public class SyncJMSMessageProducer implements EventHandler {

	private static Logger log = LoggerFactory.getLogger(SyncJMSMessageProducer.class);

	protected static final String QUEUE_NAME = "org/sakaiproject/nakamura/grouper/sync";

	@Reference
	protected ConnectionFactoryService connFactoryService;

	@Reference
	protected GrouperConfiguration grouperConfiguration;

	protected boolean transacted = true;

	@Activate
	@Modified
	public void updated(Map<String,Object> props){
		log.info("Activated/Updated");
	}

	/**
	 * @{inheritDoc}
	 * Respond to OSGi events by pushing them onto a JMS queue.
	 */
	@Override
	public void handleEvent(Event event) {
		try {
			if (ignoreEvent(event) == false){
				sendMessage(event);
			}
			else {
				log.debug("Ignoring event");
				log.trace(event.toString());
			}
		}
		catch (JMSException e) {
			log.error("There was an error sending this event to the JMS queue", e);
		}
	}

	/**
	 * Convert an OSGi {@link Event} into a JMS {@link Message} and post it on a {@link Queue}.
	 * @param event the event we're sending
	 * @throws JMSException
	 */
	private void sendMessage(Event event) throws JMSException {
		ActiveMQConnection connection;
		Session session;

		try {
			// Get the JMS plumbing
			ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost:61616");
			connection = (ActiveMQConnection) connectionFactory.createConnection();

			// http://activemq.apache.org/redelivery-policy.html
			// Configure the Queue to redeliver the message
			RedeliveryPolicy policy = connection.getRedeliveryPolicy();
			// Try .5 seconds later
			policy.setInitialRedeliveryDelay(500);
			// Increase the delay each time we fail to process the message
			policy.setBackOffMultiplier(grouperConfiguration.getJmsBackoffMultiplier());
			policy.setUseExponentialBackOff(true);
			policy.setMaximumRedeliveries(grouperConfiguration.getSyncMaxMessageRetries());
			connection.setRedeliveryPolicy(policy);

			session = connection.createSession(transacted, -1);
			Queue queue = session.createQueue(QUEUE_NAME);

			// Copy the event to the message and send it off
			MessageProducer producer = session.createProducer(queue);
			Message msg = session.createMessage();
			msg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
			msg.setJMSType(event.getTopic());
			copyEventToMessage(event, msg);

			producer.send(msg);
			session.commit();

			log.info("Sent: {} {} : messageId {}", new Object[] { event.getTopic(), event.getProperty("path"), msg.getJMSMessageID()});
			log.trace("{} : {}", msg.getJMSMessageID(), msg);
		}
		catch (JMSException jmse){
			log.error("An error occurred while communicating with the JMS queue.", jmse);
			throw jmse;
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

	/**
	 * This method indicates whether or not we should post a {@link Message} for
	 * the {@link Event}. There's some specific messages on the interesting topics
	 * that we don't want to handle for one reason or another.
	 *
	 * @param event the OSGi {@link Event} we're considering.
	 * @return whether or not to ignore this event.
	 */
	protected boolean ignoreEvent(Event event){

		// Ignore events that were posted by the Grouper system to SakaiOAE.
		// We don't want to wind up with a feedback loop between SakaiOAE and Grouper.
		String[] ignoredUsers = grouperConfiguration.getIgnoredUsersEvents();
		String eventCausedBy = (String)event.getProperty(StoreListener.USERID_PROPERTY);

		if (ignoredUsers != null && eventCausedBy != null) {
			for (String ignoreUser : ignoredUsers){
				if (eventCausedBy.equals(ignoreUser)) {
					log.trace("Ignoring event caused by {}", ignoreUser);
					return true;
				}
			}
		}

		// Ignore non-group events
		// type must be g or group
		String type = (String)event.getProperty("type");
		if (! "g".equalsIgnoreCase(type) && ! "group".equalsIgnoreCase(type)){
			log.trace("Ignoring non-group event");
			return true;
		}

		// Ignore op=acl events
		String op = (String)event.getProperty("op");
		if (op != null && op.equalsIgnoreCase("acl")){
			log.trace("Ignoring group op=acl event");
			return true;
		}

		String path = (String)event.getProperty(StoreListener.PATH_PROPERTY);
		for (String p: grouperConfiguration.getIgnoredGroups()){
			// The path is the authorizableId of the group
			if (Pattern.matches(p, path)){
				log.trace("Ignoring event because it matches {}", p);
				return true;
			}
		}

		if (!path.startsWith("g-contacts-")){
			String role = StringUtils.trimToNull(StringUtils.substringAfterLast(path, "-"));
			if (!grouperConfiguration.getPseudoGroupSuffixes().contains(role)){
				log.trace("Ignoring non-psuedogroup role : {}", role);
				return true;
			}
		}

		return false;
	}

	/**
	 * Populate a Message's properties with those from an Event.
	 *
	 * @see org.sakaiproject.nakamura.events.OsgiJmsBridge
	 *
	 * @param event the source
	 * @param message the destination
	 * @throws JMSException
	 */
	public static void copyEventToMessage(Event event, Message message) throws JMSException{
		for (String name : event.getPropertyNames()) {
			Object val = event.getProperty(name);
			if (val != null){
				message.setObjectProperty(name, EventUtils.cleanProperty(val));
			}
		}
	}
}

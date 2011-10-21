package org.sakaiproject.nakamura.grouper.event;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.name.GrouperNameManagerImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class BatchJMSMessageConsumerTest extends TestCase {

	private GrouperManager grouperManager;

	private GrouperNameManagerImpl grouperNameManager;

	private GrouperConfiguration grouperConfiguration;

	private BatchJMSMessageConsumer consumer;

	private Repository repository;

	private Session repositorySession;

	private javax.jms.Session jmsSession;

	private Message message;

	private AuthorizableManager authorizableManager;

	private User user1;
	private User user2;

	private Group group1;

	@Before
	public void setUp() throws AccessDeniedException, StorageClientException, JMSException {
		grouperManager = mock(GrouperManager.class);
		grouperConfiguration = mock(GrouperConfiguration.class);
		grouperNameManager = mock(GrouperNameManagerImpl.class);
		repository = mock(Repository.class);
		repositorySession = mock(Session.class);
		authorizableManager = mock(AuthorizableManager.class);
		message = mock(Message.class);

		ConnectionFactoryService connectionFactoryService = mock(ConnectionFactoryService.class);
		jmsSession = mock(javax.jms.Session.class);

		MessageConsumer jmsConsumer = mock(MessageConsumer.class);
		when(jmsSession.createConsumer(any(Destination.class))).thenReturn(jmsConsumer);
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		Connection connection = mock(Connection.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		when(connectionFactoryService.getDefaultPooledConnectionFactory()).thenReturn(connectionFactory);
		when(connection.createSession(true, -1)).thenReturn(jmsSession);

		consumer = new BatchJMSMessageConsumer();
		consumer.grouperManager = grouperManager;
		consumer.config = grouperConfiguration;
		consumer.grouperNameManager = grouperNameManager;
		consumer.repository = repository;
		consumer.connFactoryService = connectionFactoryService;

		consumer.activate(null);

		when(grouperConfiguration.getIgnoredUsersEvents()).thenReturn(new String[] { "admin", "grouper-admin" });
		when(grouperConfiguration.getProvisionedCoursesStem()).thenReturn("apps:sakaioae:courses:");
		when(grouperConfiguration.getInstitutionalCoursesStem()).thenReturn("inst:sis:courses:");
		when(grouperConfiguration.getInstitutionalExtensionOverrides()).thenReturn(ImmutableMap.of("lecturers", "instructors"));

		when(repository.loginAdministrative()).thenReturn(repositorySession);
		when(repositorySession.getAuthorizableManager()).thenReturn(authorizableManager);

		user1 = mock(User.class);
		user2 = mock(User.class);
		when(user1.isGroup()).thenReturn(false);
		when(user2.isGroup()).thenReturn(false);
		when(authorizableManager.findAuthorizable("user1")).thenReturn(user1);
		when(authorizableManager.findAuthorizable("user2")).thenReturn(user2);

		group1 = mock(Group.class);
		when(group1.isGroup()).thenReturn(true);
	}

	public void testCreateContactsGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		String groupId = "g-contacts-efroese";
		String grouperName = "stem:users:efroese:contacts";

		when(message.getStringProperty(Authorizable.ID_FIELD)).thenReturn(groupId);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);
		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);

		when(group1.getId()).thenReturn(groupId);
		when(group1.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn("yes");
		when(group1.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group1.getMembers()).thenReturn(new String[] { "user1", "user2" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group1);

		consumer.onMessage(message);

		verify(grouperManager).groupExists(grouperName);
		verify(grouperManager).createGroup(grouperName, "description", false);
		verify(grouperManager).addMemberships(grouperName, ImmutableList.of("user1", "user2"));
	}

	@Test
	public void testCreateGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		String groupId = "stem:some_group-student";
		String grouperName = "stem:some:group:students";
		String allName = "stem:some:group:all";
		String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;

		when(message.getStringProperty(Authorizable.ID_FIELD)).thenReturn(groupId);

		when(group1.getId()).thenReturn(groupId);
		when(group1.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(null);
		when(group1.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group1.getMembers()).thenReturn(new String[] { "user1", "user2" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group1);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(systemOfRecordName)).thenReturn(false);

		Map<String,String> props = ImmutableMap.of("uuid", "THE_GROUP_UUID");
		when(grouperManager.createGroup(systemOfRecordName, "description", true)).thenReturn(null);
		when(grouperManager.getGroupProperties(grouperName, null)).thenReturn(props);

		consumer.onMessage(message);

		verify(grouperManager).groupExists(systemOfRecordName);
		verify(grouperManager).createGroup(systemOfRecordName, "description", true);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user2", "user1"));

		verify(grouperManager).createGroup(allName, AbstractConsumer.ALL_GROUP_DESCRIPTION, false);
		verify(grouperManager).addMemberships(allName, null, "THE_GROUP_UUID");
	}

	@Test
	public void testCreateProvisionedGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		String groupId = "some_group-student";
		String grouperName = "app:sakaioae:provisioned:course:some:group:student";
		String allName = "app:sakaioae:provisioned:course:some:group:all";
		String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;

		when(message.getStringProperty(Authorizable.ID_FIELD)).thenReturn(groupId);

		when(group1.getId()).thenReturn(groupId);
		when(group1.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(true);
		when(group1.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group1.getMembers()).thenReturn(new String[] { "user1", "user2" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group1);

		when(grouperConfiguration.getProvisionedCoursesStem()).thenReturn("app:sakaioae:provisioned:course");
		when(grouperConfiguration.getInstitutionalCoursesStem()).thenReturn("inst:sis:course");

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);

		Map<String,String> props = ImmutableMap.of("uuid", "THE_GROUP_UUID");
		when(grouperManager.getGroupProperties(grouperName, null)).thenReturn(props);
		when(grouperManager.createGroup(systemOfRecordName, "description", true)).thenReturn(null);


		Map<String,String> instProps = ImmutableMap.of("uuid", "THE_INST_UUID");
		when(grouperManager.getGroupProperties("inst:sis:course:some:group:student", null)).thenReturn(instProps);

		consumer.onMessage(message);

		verify(grouperManager).groupExists(systemOfRecordName);
		verify(grouperManager).createGroup(systemOfRecordName, "description", true);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user2", "user1"));

		verify(grouperManager).addMemberships(systemOfRecordName, null, "THE_INST_UUID");

		verify(grouperManager).createGroup(allName, AbstractConsumer.ALL_GROUP_DESCRIPTION, false);
		verify(grouperManager).addMemberships(allName, null, "THE_GROUP_UUID");
	}

}

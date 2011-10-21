package org.sakaiproject.nakamura.grouper.event;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
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
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.name.GrouperNameManagerImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SyncJMSMessageConsumerTest extends TestCase {

	private GrouperManager grouperManager;

	private GrouperNameManagerImpl grouperNameManager;

	private GrouperConfiguration grouperConfiguration;

	private SyncJMSMessageConsumer syncJmsConsumer;

	private javax.jms.Session jmsSession;
	private ConnectionFactoryService connectionFactoryService;

	private Repository repository;

	private Session repositorySession;

	private AuthorizableManager authorizableManager;

	private Message message;

	private User user1;
	private User user2;

	private static final String AUTHZ_ADDED_TOPIC = "org/sakaiproject/nakamura/lite/authorizables/ADDED";

	private String groupId = "some_group-student";
	private String grouperName = "stem:some:group:students";
	private String allName = "stem:some:group:all";
	private String systemOfRecordName = grouperName + "_systemOfRecord";

	@Before
	public void setUp() throws AccessDeniedException, StorageClientException, JMSException {
		grouperManager = mock(GrouperManager.class);
		grouperConfiguration = mock(GrouperConfiguration.class);
		grouperNameManager = mock(GrouperNameManagerImpl.class);
		repository = mock(Repository.class);
		repositorySession = mock(Session.class);
		authorizableManager = mock(AuthorizableManager.class);
		connectionFactoryService = mock(ConnectionFactoryService.class);
		jmsSession = mock(javax.jms.Session.class);
		message = mock(Message.class);

		MessageConsumer consumer = mock(MessageConsumer.class);
		when(jmsSession.createConsumer(any(Destination.class))).thenReturn(consumer);
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		Connection connection = mock(Connection.class);
		when(connectionFactory.createConnection()).thenReturn(connection);
		when(connectionFactoryService.getDefaultPooledConnectionFactory()).thenReturn(connectionFactory);
		when(connection.createSession(true, -1)).thenReturn(jmsSession);

		syncJmsConsumer = new SyncJMSMessageConsumer();
		syncJmsConsumer.grouperManager = grouperManager;
		syncJmsConsumer.config = grouperConfiguration;
		syncJmsConsumer.grouperNameManager = grouperNameManager;
		syncJmsConsumer.repository = repository;
		syncJmsConsumer.connFactoryService = connectionFactoryService;
		syncJmsConsumer.activate(null);

		when(grouperConfiguration.getIgnoredUsersEvents()).thenReturn(new String[] { "admin", "grouper-admin" });
		when(grouperConfiguration.getProvisionedCoursesStem()).thenReturn("apps:sakaioae:provisioned:courses");
		when(grouperConfiguration.getInstitutionalCoursesStem()).thenReturn("inst:sis:courses");
		when(grouperConfiguration.getInstitutionalExtensionOverrides()).thenReturn(ImmutableMap.of("lecturers", "instructors"));

		when(repository.loginAdministrative()).thenReturn(repositorySession);
		when(repositorySession.getAuthorizableManager()).thenReturn(authorizableManager);

		user1 = mock(User.class);
		user2 = mock(User.class);
		when(user1.getId()).thenReturn("user1");
		when(user2.getId()).thenReturn("user2");
		when(user1.isGroup()).thenReturn(false);
		when(user2.isGroup()).thenReturn(false);

		when(authorizableManager.findAuthorizable("user1")).thenReturn(user1);
		when(authorizableManager.findAuthorizable("user2")).thenReturn(user2);
	}

	@Test
	public void testCreateProvisionedGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		String grouperName = "app:sakaioae:provisioned:course:some:group:student";
		String allName = "app:sakaioae:provisioned:course:some:group:all";
		String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;

		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getJMSType()).thenReturn(AUTHZ_ADDED_TOPIC);

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(true);
		when(group.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group.getMembers()).thenReturn(new String[] { "user1", "user2" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);

		when(grouperConfiguration.getProvisionedCoursesStem()).thenReturn("app:sakaioae:provisioned:course");
		when(grouperConfiguration.getInstitutionalCoursesStem()).thenReturn("inst:sis:course");

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);

		Map<String,String> grouperNameProps = ImmutableMap.of("uuid", "GROUPERNAME_UUID");
		when(grouperManager.createGroup(systemOfRecordName, "description", true)).thenReturn(grouperNameProps);
		when(grouperManager.getGroupProperties(grouperName, null)).thenReturn(grouperNameProps);
		when(grouperManager.getMembers(grouperName)).thenReturn(ImmutableList.of("user1"));

		Map<String,String> instProps = ImmutableMap.of("uuid", "THE_INST_UUID");
		when(grouperManager.getGroupProperties("inst:sis:course:some:group:student", null)).thenReturn(instProps);

		syncJmsConsumer.onMessage(message);

		verify(grouperManager).groupExists(systemOfRecordName);
		verify(grouperManager).createGroup(systemOfRecordName, "description", true);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user2"));

		verify(grouperManager).addMemberships(systemOfRecordName, null, "THE_INST_UUID");

		verify(grouperManager).createGroup(allName, AbstractConsumer.ALL_GROUP_DESCRIPTION, false);
		verify(grouperManager).addMemberships(allName, null, "GROUPERNAME_UUID");
	}

	@Test
	public void testCreateGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{

		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getJMSType()).thenReturn(AUTHZ_ADDED_TOPIC);

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(null);

		when(group.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group.getMembers()).thenReturn(new String[] { "user1", "grouper1" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);
		when(grouperManager.getMembers(grouperName)).thenReturn(ImmutableList.of("grouper1"));

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);

		Map<String,String> grouperNameProps = ImmutableMap.of("uuid", "GROUPERNAME_UUID");
		when(grouperManager.createGroup(systemOfRecordName, "description", true)).thenReturn(grouperNameProps);
		when(grouperManager.getGroupProperties(grouperName, null)).thenReturn(grouperNameProps);

		syncJmsConsumer.onMessage(message);

		verify(grouperManager).groupExists(systemOfRecordName);
		verify(grouperManager).createGroup(systemOfRecordName, "description", true);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user1"));

		verify(grouperManager).createGroup(allName, AbstractConsumer.ALL_GROUP_DESCRIPTION, false);
		verify(grouperManager).addMemberships(allName, null, "GROUPERNAME_UUID");
	}

	@Test
	public void testCreateGroupNoGrouperMembers() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getJMSType()).thenReturn(AUTHZ_ADDED_TOPIC);

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(null);

		when(group.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group.getMembers()).thenReturn(new String[] { "user1", "user2", "user3" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);
		when(grouperManager.getMembers(grouperName)).thenReturn(new LinkedList<String>());

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);

		Map<String,String> grouperNameProps = ImmutableMap.of("uuid", "GROUPERNAME_UUID");
		when(grouperManager.createGroup(systemOfRecordName, "description", true)).thenReturn(grouperNameProps);
		when(grouperManager.getGroupProperties(grouperName, null)).thenReturn(grouperNameProps);

		syncJmsConsumer.onMessage(message);

		verify(grouperManager).createGroup(systemOfRecordName, "description", true);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user2","user1"));

		verify(grouperManager).createGroup(allName, AbstractConsumer.ALL_GROUP_DESCRIPTION, false);
		verify(grouperManager).addMemberships(allName, null, "GROUPERNAME_UUID");
	}


	@Test
	public void testCreateContactsGroup() throws JMSException, AccessDeniedException, StorageClientException, GrouperException{
		String groupId = "g-contacts-efroese";
		String grouperName = "stem:users:efroese:contacts";

		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getJMSType()).thenReturn(AUTHZ_ADDED_TOPIC);

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn("yes");

		when(group.getProperty(AbstractConsumer.SAKAI_DESCRIPTION_PROPERTY)).thenReturn("description");
		when(group.getMembers()).thenReturn(new String[] { "user1", "user2" });
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(false);
		syncJmsConsumer.onMessage(message);

		verify(grouperManager).groupExists(grouperName);
		verify(grouperManager).createGroup(grouperName, "description", false);
		verify(grouperManager).addMemberships(grouperName, ImmutableList.of("user1", "user2"));
	}

	@Test
	public void testGetInstitutionalGroupName(){
		String grouperName = "apps:sakaioae:provisioned:courses:some:course:lecturers";
		assertEquals("inst:sis:courses:some:course:instructors", syncJmsConsumer.getInstitutionalGroupName(grouperName));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeleted() throws JMSException, GrouperException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/DELETE");

		Map<String,Object> attrs = mock(Map.class);
		when(attrs.get(GrouperManager.GROUPER_NAME_PROP)).thenReturn("some:grouper:name:role");
		when(message.getObjectProperty(StoreListener.BEFORE_EVENT_PROPERTY)).thenReturn(attrs);

		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		syncJmsConsumer.onMessage(message);
		verify(grouperManager).deleteGroup("some:grouper:name:role");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeletedNoGrouperName() throws JMSException, GrouperException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/DELETE");

		Map<String,Object> attrs = mock(Map.class);
		when(attrs.get(GrouperManager.GROUPER_NAME_PROP)).thenReturn(null);
		when(message.getObjectProperty(StoreListener.BEFORE_EVENT_PROPERTY)).thenReturn(attrs);

		syncJmsConsumer.onMessage(message);
		verifyNoMoreInteractions(grouperManager);
		verify(message, never()).acknowledge();
	}

	@Test
	public void testMembershipAdded() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP)).thenReturn("user1,user2,user3");
		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getMembers()).thenReturn(new String[0]);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).addMemberships(grouperName + "_includes", ImmutableList.of("user1", "user2"));
		verify(grouperManager).removeMemberships(grouperName + "_excludes", ImmutableList.of("user1", "user2"));
		verify(jmsSession).commit();
	}

	@Test
	public void testMembershipAddedContacts() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		String groupId = "g-contacts-efroese";
		String grouperName = "stem:users:efroese:contacts";

		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP)).thenReturn("user1,user2");

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getMembers()).thenReturn(new String[0]);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).addMemberships(grouperName, ImmutableList.of("user1", "user2"));
		verify(jmsSession).commit();
	}

	@Test
	public void testMembershipAddedEmptyString() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_ADDED_PROP)).thenReturn("");
		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);
		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(true);

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).groupExists(grouperName);
		verifyNoMoreInteractions(grouperManager);
		verify(jmsSession).commit();
	}

	@Test
	public void testMembershipRemoved() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP)).thenReturn("user1,user2,user3");
		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getMembers()).thenReturn(new String[0]);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.getMembers(grouperName)).thenReturn(ImmutableList.of("user1"));

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).getMembers(grouperName);
		verify(grouperManager).addMemberships(grouperName + "_excludes", ImmutableList.of("user1"));
		verify(grouperManager).removeMemberships(grouperName + "_includes", ImmutableList.of("user1", "user2"));
		verify(jmsSession).commit();
	}

	@Test
	public void testMembershipRemovedContacts() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		String groupId = "g-contacts-efroese";
		String grouperName = "stem:users:efroese:contacts";

		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP)).thenReturn("user1,user2,user3");

		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(group.getMembers()).thenReturn(new String[0]);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);

		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).removeMemberships(grouperName, ImmutableList.of("user1", "user2"));
		verify(jmsSession).commit();
	}

	@Test
	public void testMembershipRemovedEmptyString() throws JMSException, GrouperException, AccessDeniedException, StorageClientException{
		when(grouperConfiguration.getDeletesEnabled()).thenReturn(true);
		when(message.getJMSType()).thenReturn("org/sakaiproject/nakamura/lite/authorizables/UPDATED");
		when(message.getStringProperty("path")).thenReturn(groupId);
		when(message.getStringProperty(GrouperEventUtils.MEMBERS_REMOVED_PROP)).thenReturn("");
		Group group = mock(Group.class);
		when(group.getId()).thenReturn(groupId);
		when(authorizableManager.findAuthorizable(groupId)).thenReturn(group);
		when(group.getProperty(GrouperManager.PROVISIONED_PROPERTY)).thenReturn(false);
		when(grouperNameManager.getGrouperName(groupId)).thenReturn(grouperName);
		when(grouperManager.groupExists(grouperName)).thenReturn(true);

		syncJmsConsumer.onMessage(message);
		verify(grouperManager).groupExists(grouperName);
		verifyNoMoreInteractions(grouperManager);
		verify(jmsSession).commit();
	}
}

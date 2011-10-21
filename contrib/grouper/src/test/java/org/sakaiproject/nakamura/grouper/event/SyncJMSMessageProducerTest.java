package org.sakaiproject.nakamura.grouper.event;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.grouper.GrouperConfigurationImpl;

public class SyncJMSMessageProducerTest extends TestCase {

	private SyncJMSMessageProducer producer;
	private GrouperConfigurationImpl config;

	private Dictionary<String,Object> p;

	private Event event;

	@Before
	public void setUp() throws Exception {

		p = new Hashtable<String, Object>();
		p.put("type", "g");

		Map<String,Object> props = new HashMap<String, Object>();
		props.put(GrouperConfigurationImpl.PROP_IGNORED_USERS_EVENTS, "admin");
		props.put(GrouperConfigurationImpl.PROP_IGNORED_GROUP_PATTERN, "ignoreMe");
		props.put(GrouperConfigurationImpl.PROP_PSEUDO_GROUP_SUFFIXES, new String[] { "ps1", "ps2"});
		config = new GrouperConfigurationImpl();
		config.updated(props);
		producer = new SyncJMSMessageProducer();
		producer.grouperConfiguration = config;
	}

	@Test
	public void testIgnoresUser(){
		p.put(StoreListener.USERID_PROPERTY, "admin");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testIgnoreOpAcl(){
		p.put("op", "acl");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testIgnoresCertainGroups(){
		p.put("path", "ignoreMe");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testIgnoreBadType(){
		p.put("type", "notg");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testIgnoreNoPsuedoGroup(){
		p.put("path", "group");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testIgnoreInvalidPsuedoGroup(){
		p = new Hashtable<String, Object>();
		p.put("path", "group-ps3");
		event = new Event("topic", p);
		assertTrue(producer.ignoreEvent(event));
	}

	@Test
	public void testDontIgnoreContactsGroup(){
		p.put("type", "g");
		p.put("path", "g-contacts-erik");
		event = new Event("topic", p);
		assertFalse(producer.ignoreEvent(event));
	}

	@Test
	public void testDontIgnore(){
		p.put("type", "g");
		p.put("path", "g1-ps1");
		event = new Event("topic", p);
		assertFalse(producer.ignoreEvent(event));
	}
}
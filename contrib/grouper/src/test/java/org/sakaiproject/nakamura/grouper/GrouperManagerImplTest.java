package org.sakaiproject.nakamura.grouper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.name.GrouperNameManagerImpl;

import com.google.common.collect.ImmutableMap;

public class GrouperManagerImplTest extends TestCase {

	private GrouperManagerImpl grouperManager;
	
	private GrouperNameManagerImpl grouperNameManager;
	
	private GrouperConfiguration grouperConfiguration;

	private Repository repository; 
	
	@Before
	public void setUp(){
//		grouperConfiguration = mock(GrouperConfiguration.class);
//		grouperNameManager = mock(GrouperNameManagerImpl.class);
//		repository = mock(Repository.class);
//
//		grouperManager = new GrouperManagerImpl();
//		grouperManager.grouperConfiguration = grouperConfiguration;
//		grouperManager.grouperNameManager = grouperNameManager;
//		grouperManager.repository = repository;
//		
//		when(grouperConfiguration.getProvisionedCoursesStem()).thenReturn("apps:sakaioae:courses:");
//		when(grouperConfiguration.getInstitutionalCoursesStem()).thenReturn("inst:sis:courses:");
//		when(grouperConfiguration.getInstitutionalExtensionOverrides()).thenReturn(ImmutableMap.of("lecturers", "instructors"));
	}

	@Test
	public void testHolder(){
		assertTrue(true);
	}
	
}

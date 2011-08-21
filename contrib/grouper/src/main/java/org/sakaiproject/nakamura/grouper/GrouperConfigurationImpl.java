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
package org.sakaiproject.nakamura.grouper;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.service.cm.ConfigurationException;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@Service
@Component(metatype = true)
/**
 * @inheritDoc
 */
public class GrouperConfigurationImpl implements GrouperConfiguration {

	private static final Logger log = LoggerFactory.getLogger(GrouperConfigurationImpl.class);

	// Configurable via the ConfigAdmin services.
	@Property(boolValue = false)
	public static final String PROP_DISABLE_TESTING = "grouper.disable.for.testing";

	private static final String DEFAULT_URL = "http://localhost:9090/grouper-ws/servicesRest/1_7_000";
	@Property(value = DEFAULT_URL)
	public static final String PROP_URL = "grouper.url";

	private static final String DEFAULT_USERNAME = "GrouperSystem";
	@Property(value = DEFAULT_USERNAME)
	public static final String PROP_USERNAME = "grouper.username";

	private static final String DEFAULT_PASSWORD = "abc123";
	@Property(value = DEFAULT_PASSWORD)
	public static final String PROP_PASSWORD = "grouper.password";

	// HTTP Timeout in milliseconds
	private static final String DEFAULT_TIMEOUT = "5000";
	@Property(value = DEFAULT_TIMEOUT)
	public static final String PROP_TIMEOUT = "grouper.httpTimeout";

	private static final String[] DEFAULT_IGNORED_USERS_EVENTS = new String[] {"grouper-admin", "admin" };
	@Property(value = {"grouper-admin", "admin" }, cardinality = 9999)
	public static final String PROP_IGNORED_USERS_EVENTS = "grouper.ignore.users.events";

	private static final String DEFAULT_GROUPER_ADMIN_USERID = "grouper-admin";
	@Property(value = "grouper-admin")
	public static final String PROP_GROUPER_ADMIN_USERID = "grouper.admin.userid";

	private static final String[] DEFAULT_IGNORED_GROUP_PATTERN = {"administrators"};
	@Property(value = { "administrators" }, cardinality = 9999)
	public static final String PROP_IGNORED_GROUP_PATTERN = "grouper.ignoredGroupsPatterns";

	// TODO: A better way to generate the default list.
	private static final String[] DEFAULT_PSEUDO_GROUP_SUFFIXES =
		{"-manager", "-ta", "-lecturer", "-student", "-member"};
	@Property(value = {"-manager", "-ta", "-lecturer", "-student", "-member"}, cardinality = 9999)
	public static final String PROP_PSEUDO_GROUP_SUFFIXES = "grouper.psuedoGroup.suffixes";

	private static final String DEFAULT_CONTACTS_STEM = "edu:apps:sakaioae:users";
	@Property(value = DEFAULT_CONTACTS_STEM)
	public static final String PROP_CONTACTS_STEM = "grouper.nameprovider.contacts.stem";

	private static final String DEFAULT_SIMPLEGROUPS_STEM = "edu:apps:sakaioae:adhoc:simplegroups";
	@Property(value = DEFAULT_SIMPLEGROUPS_STEM)
	public static final String PROP_SIMPLEGROUPS_STEM = "grouper.nameprovider.adhoc.simplegroups.stem";

	private static final String DEFAULT_ADHOC_COURSES_STEM = "edu:apps:sakaioae:adhoc:course";
	@Property(value = DEFAULT_ADHOC_COURSES_STEM)
	public static final String PROP_ADHOC_COURSES_STEM = "grouper.nameprovider.adhoc.courses.stem";

	private static final String DEFAULT_PROVISIONED_COURSES_STEM = "edu:apps:sakaioae:provisioned:course";
	@Property(value = DEFAULT_PROVISIONED_COURSES_STEM)
	public static final String PROP_PROVISIONED_COURSES_STEM = "grouper.nameprovider.provisioned.courses.stem";

	private static final String DEFAULT_INSTITUTIONAL_COURSES_STEM = "inst:sis:courses";
	@Property(value = DEFAULT_INSTITUTIONAL_COURSES_STEM)
	public static final String PROP_INSTITUTIONAL_COURSES_STEM = "grouper.nameprovider.institutional.courses.stem";

	private static final String[] DEFAULT_EXTENSION_OVERRIDES = new String[0];
	@Property(value = {}, cardinality = 9999)
	public static final String PROP_EXTENSION_OVERRIDES = "grouper.extension.overrides";

	private static final String[] DEFAULT_INST_EXTENSION_OVERRIDES = new String[]{ "lecturers:instructors" };
	@Property(value = {"lecturers:instructors"}, cardinality = 9999)
	public static final String PROP_INST_EXTENSION_OVERRIDES = "grouper.institutional.extension.overrides";

	private static final boolean DEFAULT_DELETES_ENABLED = true;
	@Property(boolValue = DEFAULT_DELETES_ENABLED)
	public static final String PROP_DELETES_ENABLED = "grouper.enable.deletes";

	// Grouper configuration.
	private URL url;
	private String username;
	private String password;
	private String contactsStem;

	private String simpleGroupsStem;
	private String adhocCoursesStem;
	private String provisionedCoursesStem;
	private String institutionalCourseStem;

	// GrouperWS
	private int httpTimeout;

	// Ignore events caused by this user
	private String[] ignoreUsersEvents;
	// Ignore groups that match these regexs
	private String[] ignoredGroupPatterns;

	// Suffixes that indicate these are sakai internal groups
	private String[] pseudoGroupSuffixes;

	private Map<String, String> extensionOverrides;

	private boolean deletesEnabled;

	private boolean disableForTesting;

	private Map<String, String> institutionalExtensionOverrides;

	private String grouperAdministratorUserId;

	// -------------------------- Configuration Admin --------------------------
	/**
	 * Copy in the configuration from the config admin service.
	 *
	 * Called by the Configuration Admin service when a new configuration is
	 * detected in the web console or a config file.
	 *
	 * @see org.osgi.service.cm.ManagedService#updated
	 */
	@Activate
	@Modified
	public void updated(Map<String, Object> props) throws ConfigurationException {
		try {
			url = new URL(OsgiUtil.toString(props.get(PROP_URL), DEFAULT_URL));
		} catch (MalformedURLException mfe) {
			throw new ConfigurationException(PROP_URL, mfe.getMessage(), mfe);
		}
		username  = OsgiUtil.toString(props.get(PROP_USERNAME), DEFAULT_USERNAME);
		password  = OsgiUtil.toString(props.get(PROP_PASSWORD), DEFAULT_PASSWORD);

		contactsStem = cleanStem(OsgiUtil.toString(props.get(PROP_CONTACTS_STEM),DEFAULT_CONTACTS_STEM));
		simpleGroupsStem = cleanStem(OsgiUtil.toString(props.get(PROP_SIMPLEGROUPS_STEM),DEFAULT_SIMPLEGROUPS_STEM));
		adhocCoursesStem = cleanStem(OsgiUtil.toString(props.get(PROP_ADHOC_COURSES_STEM), DEFAULT_ADHOC_COURSES_STEM));
		provisionedCoursesStem = cleanStem(OsgiUtil.toString(props.get(PROP_PROVISIONED_COURSES_STEM), DEFAULT_PROVISIONED_COURSES_STEM));
		institutionalCourseStem = cleanStem(OsgiUtil.toString(props.get(PROP_INSTITUTIONAL_COURSES_STEM),DEFAULT_INSTITUTIONAL_COURSES_STEM));

		httpTimeout = OsgiUtil.toInteger(props.get(PROP_TIMEOUT), Integer.parseInt(DEFAULT_TIMEOUT));
		grouperAdministratorUserId = OsgiUtil.toString(props.get(PROP_GROUPER_ADMIN_USERID), DEFAULT_GROUPER_ADMIN_USERID);

		ignoreUsersEvents = OsgiUtil.toStringArray(props.get(PROP_IGNORED_USERS_EVENTS),DEFAULT_IGNORED_USERS_EVENTS);
		ignoredGroupPatterns = OsgiUtil.toStringArray(props.get(PROP_IGNORED_GROUP_PATTERN), DEFAULT_IGNORED_GROUP_PATTERN);
		pseudoGroupSuffixes = OsgiUtil.toStringArray(props.get(PROP_PSEUDO_GROUP_SUFFIXES), DEFAULT_PSEUDO_GROUP_SUFFIXES);

		deletesEnabled = OsgiUtil.toBoolean(props.get(PROP_DELETES_ENABLED), DEFAULT_DELETES_ENABLED);
		disableForTesting = OsgiUtil.toBoolean(props.get(PROP_DISABLE_TESTING), false);

		extensionOverrides = getMap(props, PROP_EXTENSION_OVERRIDES, DEFAULT_EXTENSION_OVERRIDES);
		institutionalExtensionOverrides = getMap(props, PROP_INST_EXTENSION_OVERRIDES, DEFAULT_INST_EXTENSION_OVERRIDES);

		log.debug("Configured!");
	}

	private Map<String,String> getMap(Map<String, Object> props, String property, String[] defaultValue){
		Builder<String, String> mapping = ImmutableMap.builder();
		for (String propElement: OsgiUtil.toStringArray(props.get(property), defaultValue)){
			String[] split = propElement.split(":");
			if (split.length == 2){
				mapping.put(split[0], split[1]);
			}
		}
		return mapping.build();
	}

	private String cleanStem(String stem){
		if (stem != null && stem.endsWith(":")){
			stem = stem.substring(0, stem.length() - 1);
		}
		return stem;
	}

	public URL getUrl() {
		return url;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getRestWsUrlString() {
		return url.toString();
	}

	public int getHttpTimeout() {
		return httpTimeout;
	}

	public String[] getIgnoredUsersEvents() {
		return ignoreUsersEvents;
	}

	public String getGrouperAdministratorUserId() {
		return grouperAdministratorUserId;
	}

	public String[] getIgnoredGroups() {
		return ignoredGroupPatterns;
	}

	public String[] getPseudoGroupSuffixes(){
		return pseudoGroupSuffixes;
	}

	public String getContactsStem(){
		return contactsStem;
	}

	public String getSimpleGroupsStem() {
		return simpleGroupsStem;
	}

	public String getAdhocCoursesStem() {
		return adhocCoursesStem;
	}

	public String getInstitutionalCoursesStem() {
		return institutionalCourseStem;
	}

	public String getProvisionedCoursesStem() {
		return provisionedCoursesStem;
	}

	public Map<String, String> getExtensionOverrides() {
		return extensionOverrides;
	}

	public Map<String, String> getInstitutionalExtensionOverrides() {
		return institutionalExtensionOverrides;
	}

	public boolean getDeletesEnabled(){
		return deletesEnabled;
	}

	public boolean getDisableForTesting() {
		return disableForTesting;
	}
}
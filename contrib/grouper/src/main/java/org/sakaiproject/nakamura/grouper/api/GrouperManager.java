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
package org.sakaiproject.nakamura.grouper.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;

/**
 * Manage the interaction with a Grouper WS server.
 */
public interface GrouperManager {

	// The property stored on a Group Authorizable that indicates its group in grouper.
	public static final String GROUPER_NAME_PROP = "grouper:name";

	// Was this group provisioned by grouper in the first place?
	public static final String PROVISIONED_PROPERTY = "grouper:provisioned";

	/*
	 * This is a special kind of grouper group that maintains adhoc memberships
	 * along with a group that is considered the system of record.
	 *
	 * To represent group1 we have in grouper:
	 *
	 * 1.  group1 include
	 * 2.  group1 exclude
	 * 3.  group1 system of record
	 * 4.  group1 system of record union include
	 * 5.  group1, (4) - (2).  This is the final group.
	 */
	public static final String INCLUDE_EXCLUDE_GROUP_TYPE = "addIncludeExclude";
	public static final String INCLUDE_SUFFIX = "_includes";
	public static final String EXCLUDE_SUFFIX = "_excludes";
	public static final String SYSTEM_OF_RECORD_SUFFIX = "_systemOfRecord";

	/**
	 * Create a Grouper group for a nakamura group
	 * @param groupId the id of the {@link Authorizable} for this group.
	 * @return true if the group was created in Grouper.
	 */
	public Map<String,String> createGroup(String grouperName, String description, boolean addIncludeExclude) throws GrouperException;


	/**
	 * Delete a group from Grouper.
	 * @param grouperName the grouper name
	 * @param attributes the properties of this group.
	 * @throws GrouperException
	 */
	public void deleteGroup(String grouperName) throws GrouperException;

	/**
	 * Add members to a Grouper group.
	 * @param grouperName the full grouper name
	 * @param membersToAdd the member id's to add to this group.
	 */
	public void addMemberships(String grouperName, Collection<String> membersToAdd) throws GrouperException;

	/**
	 * Add members to a Grouper group.
	 * @param grouperName the full grouper name
	 * @param membersToAdd the member id's to add to this group.
	 * @param groupToAdd a single grouper name to add as a member.
	 */
	public void addMemberships(String grouperName, Collection<String> membersToAdd, String groupToAdd) throws GrouperException;


	/**
	 * Add members to a Grouper group.
	 * @param grouperName the full grouper name
	 * @param membersToRemove the member id's to remove from this group.
	 */
	public void removeMemberships(String grouperName, Collection<String> membersToRemove) throws GrouperException;

	/**
	 * Add members to a Grouper group.
	 * @param grouperName the full grouper name.
	 * @param membersToRemove the member id's to remove from this group.
	 * @param groupUuidToRemove a group uuid to remove.
	 */
	public void removeMemberships(String grouperName, Collection<String> membersToRemove, String groupUuidToRemove) throws GrouperException;

	/**
	 * Whether or not a group exists in grouper.
	 * @param grouperName
	 * @return true if the group exists
	 */
	public boolean groupExists(String grouperName) throws GrouperException;

	/**
	 * Retrieve a group's attributes from Grouper.
	 * @param grouperName the full grouper name
	 * @return A map of the simple attributes of the grouper group, null if not found.
	 * @throws GrouperException
	 */
	public Map<String,String> getGroupProperties(String grouperName, String uuid) throws GrouperException;

	/**
	 * Retrieve a group's members from Grouper.
	 * @param grouperName
	 * @return a list of subject ids
	 * @throws GrouperException
	 */
	public List<String> getMembers(String grouperName) throws GrouperException;
}

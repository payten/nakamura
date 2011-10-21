package org.sakaiproject.nakamura.grouper.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.name.api.GrouperNameManager;
import org.sakaiproject.nakamura.grouper.util.GroupUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

@Component(componentAbstract=true)
public class AbstractConsumer {

	private static Logger log = LoggerFactory.getLogger(SyncJMSMessageConsumer.class);

	public static final String SAKAI_DESCRIPTION_PROPERTY = "sakai:group-description";

	public static final String ALL_GROUP_DESCRIPTION = "All members of sibling groups";
	public static final String ALL_GROUP_EXTENSION = "all";

	@Reference
	protected GrouperManager grouperManager;

	@Reference
	protected GrouperNameManager grouperNameManager;

	@Reference
	protected GrouperConfiguration config;

	/**
	 * @param group the nakamura Group
	 * @param grouperName the name for this group in Group
	 * @param session a sparse map content session for DB-rleated queries.
	 * @throws GrouperException
	 */
	protected void doCreateGroup(Group group, String grouperName, Session session) throws GrouperException{

		String description = (String)group.getProperty(SAKAI_DESCRIPTION_PROPERTY);
		boolean isProvisioned = group.getProperty(GrouperManager.PROVISIONED_PROPERTY) != null;

		// Contacts group
		// Create the group if it doesn't exist and add the current membership.
		if (GroupUtil.isContactsGroup(group.getId())){
			if (grouperManager.groupExists(grouperName) == false){
				grouperManager.createGroup(grouperName, description, false);
				grouperManager.addMemberships(grouperName, cleanMemberNames(Arrays.asList(group.getMembers()), session));
			}
		}
		// Course Group or Simple Group
		// This should be a pseudoGroup with a role extension on it (some_group-student)
		else {
			String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;

			if (grouperManager.groupExists(systemOfRecordName) == true){
				log.info("{} already exists.", grouperName);
				return;
			}
			// CREATE app:sakaioae:some:course:students_systemOfRecord
			grouperManager.createGroup(systemOfRecordName, description, true);
			// FIND app:sakaioae:some:course:students
			Map<String,String> grouperNameProperties = grouperManager.getGroupProperties(grouperName, null);

			if (grouperNameProperties != null){ // FOUND app:sakaioae:some:course:students

				createAllGroup(grouperName, grouperNameProperties);

				if (isProvisioned && grouperName.startsWith(config.getProvisionedCoursesStem())){
					addSystemOfRecordMembership(grouperName);
				}
			}
			else {
				log.error("Not able to create the {} group.", grouperName);
			}
		}
	}

	protected void syncMemberships(Group group, String grouperName, Session session) throws GrouperException {
		// Get the current membership in grouper,
		// subtract it from the current sakai membership, and add the rest.
		Set<String> currentGrouperMembers = new HashSet<String>(grouperManager.getMembers(grouperName));
		Set<String> currentSakaiMembers = new HashSet<String>();
		String[] currentSakaiMembersArray = group.getMembers();
		for (String member: currentSakaiMembersArray){
			currentSakaiMembers.add(member);
		}
		currentSakaiMembers.removeAll(currentGrouperMembers);
		List<String> cleanedSakaiMembers = cleanMemberNames(currentSakaiMembers, session);
		grouperManager.addMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, cleanedSakaiMembers);
		log.info("Add members. {} : {}", grouperName + GrouperManager.INCLUDE_SUFFIX, StringUtils.join(cleanedSakaiMembers, ", "));
	}

	/**
	 * Create the :all role-up group for this course.
	 * Add the grouperName group as a member of the :all group.
	 *
	 * The all group should have the other role groups as members.
	 * @param grouperName the name of the group that triggered this event
	 * @param properties the properties of the group that triggered the event
	 * @throws GrouperException
	 */
	private void createAllGroup(String grouperName, Map<String,String> properties) throws GrouperException {
		// Look up the grouper UUID for app:sakaioae:some:course:students so we can add it to :all
		String groupUUID = properties.get("uuid");
		// Create the app:sakaioae:some:course:all group
		String allGroupName = StringUtils.substringBeforeLast(grouperName, ":") + ":" + ALL_GROUP_EXTENSION;

		if (!grouperManager.groupExists(allGroupName)){
			grouperManager.createGroup(allGroupName, ALL_GROUP_DESCRIPTION, false);
			log.info("Created All roll-up group {}", allGroupName);
		}

		List<String> members = grouperManager.getMembers(allGroupName);
		List<String> membersThatAreGroups = new ArrayList<String>();
		for (String memberId: members){
			if(memberId.length() == 32){
				membersThatAreGroups.add(memberId);
			}
		}

		log.debug("Found {} group members in {} : {}",
				new Object[] { membersThatAreGroups.size(),
					allGroupName,
					StringUtils.join(membersThatAreGroups.toArray(), ",")});

		// For each of the groups in the :all group members look the group up
		// Remove the institutional role group if we find it there
		for (String memberUUID : membersThatAreGroups){
			Map<String,String> props = grouperManager.getGroupProperties(null, memberUUID);
			if (props != null){
				String memberName = props.get("name");
				String institutionalRoleGroupName = getInstitutionalGroupName(grouperName);
				if (memberName != null && memberName.equals(institutionalRoleGroupName)){
					grouperManager.removeMemberships(allGroupName, null, props.get("uuid"));
					break;
				}
			}
		}

		// Add this group to the all group.
		grouperManager.addMemberships(allGroupName, null, groupUUID);
	}

	/**
	 * Add the institutional group for grouperName as a member of grouperName_systemOfRecord
	 * @param grouperName the name of the group that triggered this event.
	 * @throws GrouperException
	 */
	private void addSystemOfRecordMembership(String grouperName) throws GrouperException {
		String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;
		// inst:sis:course:some:course:students
		String instGroupName = getInstitutionalGroupName(grouperName);
		Map<String,String> institutional = grouperManager.getGroupProperties(instGroupName, null);
		// ADD MEMBER to group: app:sakaioae:some:course:students_systemOfRecord
		//               member: inst:sis:course:some:course:students
		if (institutional != null){
			grouperManager.addMemberships(systemOfRecordName, null, institutional.get("uuid"));
			log.info("Added {} to {}", instGroupName, systemOfRecordName);
		}
		else {
			log.debug("No institutional group at {} for {}", instGroupName, systemOfRecordName);
		}
	}

	protected void doAddMemberships(Group group, String grouperName, List<String> membersAdded, Session session) throws GrouperException {
		if (!grouperManager.groupExists(grouperName)){
			doCreateGroup(group, grouperName, session);
		}

		// Clean up and bail early if possible
		membersAdded = cleanMemberNames(membersAdded, session);
		if (membersAdded.isEmpty()){
			return;
		}

		// Contacts groups are straightforward
		if (GroupUtil.isContactsGroup(group.getId())){
			grouperManager.addMemberships(grouperName, membersAdded);
		}
		// Course Group or Simple Group
		else {
			// Only add members to the _includes group if they're not in the grouper group
			Set<String> currentMembers = Sets.newHashSet(grouperManager.getMembers(grouperName));
			List<String> addToIncludes = new ArrayList<String>();
			for (String memberToAdd : membersAdded){
				if (!currentMembers.contains(memberToAdd)){
					addToIncludes.add(memberToAdd);
				}
			}
			grouperManager.addMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, addToIncludes);
			grouperManager.removeMemberships(grouperName + GrouperManager.EXCLUDE_SUFFIX, membersAdded);
		}
	}

	protected void doRemoveMemberships(Group group, String grouperName, List<String> membersRemoved, Session session) throws GrouperException {
		if (!grouperManager.groupExists(grouperName)){
			doCreateGroup(group, grouperName, session);
		}

		// Clean up and bail early if possible
		membersRemoved = cleanMemberNames(membersRemoved, session);
		if (membersRemoved.isEmpty()){
			return;
		}

		// Contacts groups are straightforward
		if (GroupUtil.isContactsGroup(group.getId())){
			grouperManager.removeMemberships(grouperName, membersRemoved);
		}
		// Course Group or Simple Group
		else {
			// Only add members to the _excludes group if they're in the grouper group
			Set<String> currentMembers = Sets.newHashSet(grouperManager.getMembers(grouperName));
			List<String> addToExcludes = new ArrayList<String>();
			for (String memberToRemove : membersRemoved){
				if (currentMembers.contains(memberToRemove)){
					addToExcludes.add(memberToRemove);
				}
			}
			grouperManager.addMemberships(grouperName + GrouperManager.EXCLUDE_SUFFIX, addToExcludes);
			grouperManager.removeMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, membersRemoved);
		}
	}

	/**
	 * Filter a list of Authorizable ids to only include users to send to grouper.
	 * @param authorizableIds a list of authorizableIds
	 * @param session a sparse map content session used to look things up.
	 * @return a list of the users to send to grouper as members.
	 * @throws GrouperException
	 */
	private List<String> cleanMemberNames(Collection<String> authorizableIds, Session session) throws GrouperException{
		List<String> cleaned = new ArrayList<String>();
		for (String authorizableId: authorizableIds){
			try {
				AuthorizableManager authorizableManager = session.getAuthorizableManager();
				Authorizable authorizable = null;
				authorizable = authorizableManager.findAuthorizable(authorizableId);
				if (authorizableId.equals(config.getGrouperAdministratorUserId())
						|| authorizableId.equals("admin")){
					// Don't bother adding the admin user as a member.
					// It probably doesn't exist in grouper.
					continue;
				}
				if (authorizable == null || authorizable.isGroup()){
					log.debug("cleanMemberNames: {} is not a valid User id.", authorizableId);
					continue;
				}
			}
			catch (StorageClientException sce) {
				throw new GrouperException("Unable to fetch authorizable");
			}
			catch (AccessDeniedException ade) {
				throw new GrouperException("Unable to fetch authorizable. Access Denied.");
			}
			cleaned.add(authorizableId);
		}

		return cleaned;
	}

	/**
	 * Convert a provisioned group into its institutional counterpart.
	 * @param grouperName a group in the provisioned course stem
	 * @return the corresponding group in the institutional course stem
	 */
	protected String getInstitutionalGroupName(String grouperName){

		String instGroupName = grouperName.replaceFirst(
				config.getProvisionedCoursesStem(),
				config.getInstitutionalCoursesStem());

		// use the institutional extension map to get the extension of the inst group
		Map<String,String> extensionMap = config.getInstitutionalExtensionOverrides();
		String extension = StringUtils.substringAfterLast(instGroupName, ":");

		if (extensionMap.containsKey(extension)){
			instGroupName = StringUtils.substringBeforeLast(instGroupName, ":")
			+ ":" + extensionMap.get(extension);
		}
		return instGroupName;
	}
}

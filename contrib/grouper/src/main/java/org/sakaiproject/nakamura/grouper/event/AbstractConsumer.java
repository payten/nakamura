package org.sakaiproject.nakamura.grouper.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
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

@Component(componentAbstract=true)
public class AbstractConsumer {

	private static final String DEFAULT_DESCRIPTION = "description";

	private static Logger log = LoggerFactory.getLogger(SyncJMSMessageConsumer.class);

	public static final String SAKAI_DESCRIPTION_PROPERTY = "sakai:group-description";

	public static final String ALL_GROUP_DESCRIPTION = "All members of sibling groups";

	@Reference
	protected GrouperManager grouperManager;

	@Reference
	protected GrouperNameManager grouperNameManager;

	@Reference
	protected GrouperConfiguration config;

	protected void doCreateGroup(Group group, String grouperName, org.sakaiproject.nakamura.api.lite.Session session) throws GrouperException{

		String description = (String)group.getProperty(SAKAI_DESCRIPTION_PROPERTY);
		boolean isProvisioned = group.getProperty(GrouperManager.PROVISIONED_PROPERTY) != null;

		if (GroupUtil.isContactsGroup(group.getId())){
			if (grouperManager.groupExists(grouperName) == false){
				grouperManager.createGroup(grouperName, description, false);
				grouperManager.addMemberships(grouperName, cleanMemberNames(Arrays.asList(group.getMembers()), session));
			}
		}
		else {
			String systemOfRecordName = grouperName + GrouperManager.SYSTEM_OF_RECORD_SUFFIX;

			if (grouperManager.groupExists(systemOfRecordName) == true){
				return;
			}
			// CREATE app:sakaioae:some:course:students_systemOfRecord
			grouperManager.createGroup(systemOfRecordName, description, true);
			// FIND app:sakaioae:some:course:students
			Map<String,String> grouperNameProperties = grouperManager.getGroupProperties(grouperName);

			// FOUND app:sakaioae:some:course:students
			if (grouperNameProperties != null){
				// Look up the grouper UUID for app:sakaioae:some:course:students so we can add it to :all
				String groupUUID = grouperNameProperties.get("uuid");
				// Create the app:sakaioae:some:course:all group
				String allName = StringUtils.substringBeforeLast(grouperName, ":") + ":" + "all";
				grouperManager.createGroup(allName, ALL_GROUP_DESCRIPTION, false);
				log.info("Created All roll-up group {}", allName);
				// Add this group to the all group.
				grouperManager.addMemberships(allName, null, groupUUID);

				if (isProvisioned){
					if (grouperName.startsWith(config.getProvisionedCoursesStem())){
						// inst:sis:course:some:course:students
						String instGroupName = getInstitutionalGroupName(grouperName);
						Map<String,String> institutional = grouperManager.getGroupProperties(instGroupName);
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
				}

				// Get the current membership in grouper,
				// subtract it from the current sakai membership, and add the rest.
				List<String> currentGrouperMembers = grouperManager.getMembers(grouperName);
				List<String> currentSakaiMembers = new LinkedList<String>();
				CollectionUtils.addAll(currentSakaiMembers, group.getMembers());
				currentSakaiMembers.removeAll(currentGrouperMembers);
				grouperManager.addMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, currentSakaiMembers);
			}
		}
	}

	protected void doAddMemberships(Group group, String grouperName, List<String> membersAdded) throws GrouperException {
		if (GroupUtil.isContactsGroup(group.getId())){
			if (!grouperManager.groupExists(grouperName)){
				grouperManager.createGroup(grouperName, DEFAULT_DESCRIPTION, false);
			}
			grouperManager.addMemberships(grouperName, membersAdded);
		}
		else {
			if (!grouperManager.groupExists(grouperName)){
				grouperManager.createGroup(grouperName, DEFAULT_DESCRIPTION, true);
			}
			grouperManager.addMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, membersAdded);
			grouperManager.removeMemberships(grouperName + GrouperManager.EXCLUDE_SUFFIX, membersAdded);
		}
	}

	protected void doRemoveMemberships(Group group, String grouperName, List<String> membersRemoved) throws GrouperException {
		if (GroupUtil.isContactsGroup(group.getId())){
			if (!grouperManager.groupExists(grouperName)){
				grouperManager.removeMemberships(grouperName, membersRemoved);
			}
		}
		else {
			if (!grouperManager.groupExists(grouperName)){
				grouperManager.createGroup(grouperName, DEFAULT_DESCRIPTION, true);
			}
			grouperManager.addMemberships(grouperName + GrouperManager.EXCLUDE_SUFFIX, membersRemoved);
			grouperManager.removeMemberships(grouperName + GrouperManager.INCLUDE_SUFFIX, membersRemoved);
		}
	}

	protected List<String> cleanMemberNames(List<String> memberIds, org.sakaiproject.nakamura.api.lite.Session session) throws GrouperException{
		List<String> cleaned = new ArrayList<String>();
		for (String memberId: memberIds){
			try {
				AuthorizableManager authorizableManager = session.getAuthorizableManager();
				Authorizable authorizable = null;
				authorizable = authorizableManager.findAuthorizable(memberId);
				if (authorizable == null || authorizable.isGroup()){
					log.debug("cleanMemberNames: {} is not a valid User id.", memberId);
					continue;
				}
				if (memberId.equals(config.getGrouperAdministratorUserId())
						|| memberId.equals("admin")){
					// Don't bother adding the admin user as a member.
					// It probably doesn't exist in grouper.
					continue;
				}
			}
			catch (StorageClientException sce) {
				throw new GrouperException("Unable to fetch authorizable");
			}
			catch (AccessDeniedException ade) {
				throw new GrouperException("Unable to fetch authorizable. Access Denied.");
			}
			cleaned.add(memberId);
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

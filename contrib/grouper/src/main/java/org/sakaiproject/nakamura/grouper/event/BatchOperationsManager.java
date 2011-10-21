package org.sakaiproject.nakamura.grouper.event;

/**
 * Separates the batch logic from the Web Console plugin functionality.
 */
public interface BatchOperationsManager {

	/**
	 * Cause all group and course information for provisioned groups to be sent to Grouper
	 * @throws SolrServerException
	 * @throws JMSException
	 */
	public abstract void doProvisionedGroups() throws Exception;

	/**
	 * Cause all group and course information for adhoc groups to be sent to Grouper
	 * @throws SolrServerException
	 * @throws JMSException
	 */
	public abstract void doAdhocGroups() throws Exception;

	/**
	 * Cause all contact information to be sent to Grouper
	 * @throws SolrServerException
	 * @throws JMSException
	 */
	public abstract void doContacts() throws Exception;

	/**
	 * Cause one group to be sync'd to Grouper.
	 * Useful for debugging/testing.
	 * @param groupId the id of the group to sync.
	 * @throws JMSException
	 */
	public abstract void doOneGroup(String groupId) throws Exception;

}
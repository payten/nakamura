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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.grouper.api.GrouperConfiguration;
import org.sakaiproject.nakamura.grouper.api.GrouperManager;
import org.sakaiproject.nakamura.grouper.exception.GrouperException;
import org.sakaiproject.nakamura.grouper.exception.GrouperWSException;
import org.sakaiproject.nakamura.grouper.name.api.GrouperNameManager;
import org.sakaiproject.nakamura.grouper.util.GroupUtil;
import org.sakaiproject.nakamura.grouper.util.GrouperHttpUtil;
import org.sakaiproject.nakamura.grouper.util.GrouperJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.MapMaker;

import edu.internet2.middleware.grouperClient.ws.beans.WsAddMemberResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsDeleteMemberResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsFindGroupsResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGetMembersResult;
import edu.internet2.middleware.grouperClient.ws.beans.WsGetMembersResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroup;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupDeleteResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupDetail;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupLookup;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupSaveResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupToSave;
import edu.internet2.middleware.grouperClient.ws.beans.WsQueryFilter;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestAddMemberRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestDeleteMemberRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestFindGroupsRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestGetMembersRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestGroupDeleteRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsRestGroupSaveRequest;
import edu.internet2.middleware.grouperClient.ws.beans.WsSubject;
import edu.internet2.middleware.grouperClient.ws.beans.WsSubjectLookup;

@Service
@Component
public class GrouperManagerImpl implements GrouperManager {

	private static final Logger log = LoggerFactory.getLogger(GrouperManagerImpl.class);

	@Reference
	protected GrouperConfiguration grouperConfiguration;

	@Reference
	protected GrouperNameManager grouperNameManager;

	@Reference
	protected Repository repository;

	ConcurrentMap<String,Map<String,String>> existsInGrouperCache;

	@Activate
	@Modified
	public void modified(){
		existsInGrouperCache = new MapMaker().expiration(5, TimeUnit.SECONDS).makeMap();
	}

	public Map<String,String> createGroup(String grouperName, String description, boolean addIncludeExclude) throws GrouperException {

		JSONObject resultJSON = null;
		String grouperExtension = StringUtils.substringAfterLast(grouperName, ":");

		// Fill out the group save request beans
		WsRestGroupSaveRequest groupSave = new WsRestGroupSaveRequest();
		WsGroupToSave wsGroupToSave = new WsGroupToSave();
		wsGroupToSave.setWsGroupLookup(new WsGroupLookup(grouperName, null));
		WsGroup wsGroup = new WsGroup();
		wsGroup.setDescription(description);
		wsGroup.setDisplayExtension(grouperExtension);
		wsGroup.setExtension(grouperExtension);
		wsGroup.setName(grouperName);

		if (addIncludeExclude){
			// More detailed group info for the addIncludeExclude group type
			WsGroupDetail groupDetail = new WsGroupDetail();
			groupDetail.setTypeNames(new String[] { INCLUDE_EXCLUDE_GROUP_TYPE });
			wsGroup.setDetail(groupDetail);
			wsGroup.setName(grouperName);
			wsGroup.setDisplayExtension(grouperExtension);
			wsGroup.setExtension(grouperExtension);
		}

		// Package up the request
		wsGroupToSave.setWsGroup(wsGroup);
		wsGroupToSave.setCreateParentStemsIfNotExist("T");
		groupSave.setWsGroupToSaves(new WsGroupToSave[]{ wsGroupToSave });

		// POST and parse the response
		JSONObject response = post("/groups", groupSave);
		WsGroupSaveResults results = (WsGroupSaveResults)JSONObject.toBean(
				response.getJSONObject("WsGroupSaveResults"), WsGroupSaveResults.class);

		// Error handling is a bit awkward. If the group already exists its not a problem
		if (!"T".equals(results.getResultMetadata().getSuccess())) {
			if (results.getResults().length > 0 &&
					results.getResults()[0].getResultMetadata().getResultMessage().contains("already exists")){
				log.debug("Group already existed in grouper at {}", grouperName);
			}
			else {
				throw new GrouperWSException(results);
			}
		}
		else {
			resultJSON = response;
		}
		// Get the properties of the newly created group from the response
		Builder<String, String> groupProperties = new ImmutableMap.Builder<String,String>();
		for(Object key : resultJSON.keySet()){
			Object val = resultJSON.get(key);
			if (key instanceof String && val instanceof String){
				groupProperties.put((String)key, (String)val);
			}
		}

		log.debug("Created Grouper Group. name: {}, description: {}, addIncludeExclude: {}",
				new Object[]{ grouperName, description, addIncludeExclude });

		return groupProperties.build();
	}

	/**
	 * Delete a group from Grouper.
	 * @param grouperName the full name of the group
	 * @throws GrouperException
	 */
	public void deleteGroup(String grouperName) throws GrouperException{
		try {
			// Fill out the group delete request beans
			WsRestGroupDeleteRequest groupDelete = new WsRestGroupDeleteRequest();
			if (GroupUtil.isContactsGroup(grouperName)){
				groupDelete.setWsGroupLookups(new WsGroupLookup[]{new WsGroupLookup(grouperName, null)});
			}
			else {
				groupDelete.setWsGroupLookups(new WsGroupLookup[]{
					new WsGroupLookup(grouperName, null),
					new WsGroupLookup(grouperName + INCLUDE_SUFFIX, null),
					new WsGroupLookup(grouperName + EXCLUDE_SUFFIX, null) });
			}
			// Send the request and parse the result, throwing an exception on failure.
			JSONObject response = post("/groups", groupDelete);
			WsGroupDeleteResults results = (WsGroupDeleteResults)JSONObject.toBean(
					response.getJSONObject("WsGroupDeleteResults"), WsGroupDeleteResults.class);
			if (!"T".equals(results.getResultMetadata().getSuccess())) {
					throw new GrouperWSException(results);
			}
		}
		catch (Exception e) {
			throw new GrouperException(e.getMessage());
		}
		log.info("Deleted grouper group {}", grouperName);
	}

	public void addMemberships(String grouperName, Collection<String> subjectsToAdd) throws GrouperException{
		addMemberships(grouperName, subjectsToAdd, null);
	}

	public void addMemberships(String grouperName, Collection<String> subjectsToAdd, String groupToAdd) throws GrouperException{

		WsRestAddMemberRequest addMembers = new WsRestAddMemberRequest();
		Set<WsSubjectLookup> subjectLookups = new HashSet<WsSubjectLookup>();

		if (subjectsToAdd == null){
			subjectsToAdd = new ArrayList<String>();
		}

		if (!subjectsToAdd.isEmpty()){
			// Each subjectId must have a lookup
			for (String subjectId: subjectsToAdd){
				subjectLookups.add(new WsSubjectLookup(subjectId, null, null));
			}
		}

		// Supporting one group for now since that's what the Grouper WS API supports.
		if (groupToAdd != null){
			subjectLookups.add(new WsSubjectLookup(groupToAdd, "g:gsa", null));
			subjectsToAdd.add(groupToAdd);
		}

		String added = StringUtils.join(subjectsToAdd, ',');
		log.debug("Adding members: Group = {} members = {}", grouperName, added);

		if (!subjectLookups.isEmpty()){
			addMembers.setSubjectLookups(subjectLookups.toArray(new WsSubjectLookup[subjectLookups.size()]));
			// Don't overwrite the entire group membership. just add to it.
			addMembers.setReplaceAllExisting("F");

			String urlPath = "/groups/" + grouperName + "/members";
			urlPath = urlPath.replace(":", "%3A");
			// Send the request and parse the result, throwing an exception on failure.
			JSONObject response = post(urlPath, addMembers);
			WsAddMemberResults results = (WsAddMemberResults)JSONObject.toBean(
					response.getJSONObject("WsAddMemberResults"), WsAddMemberResults.class);
			if (!"T".equals(results.getResultMetadata().getSuccess())) {
					throw new GrouperWSException(results);
			}
			log.info("Added members: Group = {} members = {}, {}", new String[]{ grouperName, added } );
		}
		else {
			log.error("No members to add.");
		}
	}

	public void removeMemberships(String grouperName, Collection<String> subjectsToRemove) throws GrouperException{
		removeMemberships(grouperName, subjectsToRemove, null);
	}

	public void removeMemberships(String grouperName, Collection<String> subjectsToRemove, String groupToRemove)
	throws GrouperException {
		WsRestDeleteMemberRequest deleteMembers = new WsRestDeleteMemberRequest();
		Set<WsSubjectLookup> subjectLookups = new HashSet<WsSubjectLookup>();

		if (subjectsToRemove == null){
			subjectsToRemove = new ArrayList<String>();
		}

		// Each subjectId must have a lookup
		for (String subjectId: subjectsToRemove){
			subjectLookups.add(new WsSubjectLookup(subjectId, null, null));
		}

		// Supporting one group for now since that's what the Grouper WS API supports.
		if (groupToRemove != null){
			subjectLookups.add(new WsSubjectLookup(groupToRemove, "g:gsa", null));
			subjectsToRemove.add(groupToRemove);
		}
		String removed = StringUtils.join(subjectsToRemove, ',');
		log.debug("Removing members: Group = {} members = {}", grouperName, removed);

		if (!subjectLookups.isEmpty()){
			deleteMembers.setSubjectLookups(subjectLookups.toArray(new WsSubjectLookup[subjectLookups.size()]));
			String urlPath = "/groups/" + grouperName + "/members";
			urlPath = urlPath.replace(":", "%3A");
			JSONObject response = post(urlPath, deleteMembers);

			WsDeleteMemberResults results = (WsDeleteMemberResults)JSONObject.toBean(
					response.getJSONObject("WsDeleteMemberResults"), WsDeleteMemberResults.class);
			if (!"T".equals(results.getResultMetadata().getSuccess())) {
				throw new GrouperWSException(results);
			}

			log.info("Removed members: Group = {} members = {}", grouperName, removed);
		}
		else {
			log.error("No members to remove.");
		}
	}

	public boolean groupExists(String grouperName) throws GrouperException{
		return getGroupProperties(grouperName, null) != null;
	}

	public Map<String,String> getGroupProperties(String grouperName, String uuid) throws GrouperException {

		if (grouperName != null && existsInGrouperCache.containsKey(grouperName)){
			log.debug("getGroupProperties cache hit.");
			return existsInGrouperCache.get(grouperName);
		}

		// Fill out the group save request beans
		WsRestFindGroupsRequest groupFind = new WsRestFindGroupsRequest();
		WsQueryFilter wsQueryFilter = new WsQueryFilter();
		if (grouperName != null && uuid == null){
			wsQueryFilter.setGroupName(grouperName);
			wsQueryFilter.setQueryFilterType("FIND_BY_GROUP_NAME_EXACT");
		}
		else if (uuid != null){
			wsQueryFilter.setGroupUuid(uuid);
			wsQueryFilter.setQueryFilterType("FIND_BY_GROUP_UUID");
		}

	    groupFind.setWsQueryFilter(wsQueryFilter);

		// POST and parse the response
		JSONObject response = post("/groups", groupFind);
		WsFindGroupsResults results = (WsFindGroupsResults)JSONObject.toBean(
				response.getJSONObject("WsFindGroupsResults"), WsFindGroupsResults.class);

		WsGroup wsgroup = null;
		// Error handling is a bit awkward. If the group already exists its not a problem
		if ("T".equals(results.getResultMetadata().getSuccess())) {
			if (results.getGroupResults() != null){
				wsgroup = results.getGroupResults()[0];
			}
		}
		Map<String,String> props = null;
		if (wsgroup != null){
			Builder<String,String> groupResult = ImmutableMap.builder();
			if(wsgroup.getName() != null){
				groupResult.put("name", wsgroup.getName());
			}
			if(wsgroup.getUuid() != null){
				groupResult.put("uuid", wsgroup.getUuid());
			}
			if(wsgroup.getDescription() != null){
				groupResult.put("description", wsgroup.getDescription());
			}
			if(wsgroup.getExtension() != null){
				groupResult.put("extension", wsgroup.getExtension());
			}
			if(wsgroup.getDisplayExtension() != null){
				groupResult.put("displayExtension", wsgroup.getDisplayExtension());
			}
			props = groupResult.build();
			if (props.containsKey("name")){
				grouperName = props.get("name");
			}
			if (grouperName != null){
				existsInGrouperCache.put(grouperName, props);
			}
		}
		if (props == null || props.isEmpty()){
			log.info("getGroupProperties : {} : not found", grouperName);
			return null;
		}
		log.info("getGroupProperties : {} : found", grouperName);
		return props;
	}

	public List<String> getMembers(String grouperName) throws GrouperException {

		WsRestGetMembersRequest getMembers = new WsRestGetMembersRequest();
		getMembers.setWsGroupLookups(new WsGroupLookup[] { new WsGroupLookup(grouperName, null) });

		JSONObject response = post("/groups", getMembers);
		WsGetMembersResults results = (WsGetMembersResults)JSONObject.toBean(
				response.getJSONObject("WsGetMembersResults"), WsGetMembersResults.class);

		List<String> members = new ArrayList<String>();
		if ("T".equals(results.getResultMetadata().getSuccess())
				&& results.getResults() != null){

			for (WsGetMembersResult result : results.getResults()){
				if (result.getWsSubjects() != null){
					for (WsSubject subject : result.getWsSubjects()){
						if (subject != null){
							members.add(subject.getId());
						}
					}
				}
			}
		}
		log.info("Found {} members for {}", members.size(), grouperName);
		log.debug("members: {}", StringUtils.join(members, ", "));
		return members;
	}

	/**
	 * Issue an HTTP POST to Grouper Web Services
	 *
	 * TODO: Is there a better type for the grouperRequestBean parameter?
	 *
	 * @param grouperRequestBean a Grouper WS bean representing a grouper action
	 * @return the parsed JSON response
	 * @throws HttpException
	 * @throws IOException
	 * @throws GrouperException
	 */
	private JSONObject post(String uri, Object grouperRequestBean) throws GrouperException  {
		try {
		    JSONObject response;
		    if (grouperConfiguration.getDisableForTesting()){
		    	response =  new JSONObject();
		    	response.put("status.code", "200");
		    	response.put("status.message", "Fake Success!");
		    }
		    else {
				HttpClient client = GrouperHttpUtil.getHttpClient(grouperConfiguration);
				String grouperWsRestUrl = grouperConfiguration.getUrl() + uri;
				log.debug("Preparing POST to {}", grouperWsRestUrl);
		        PostMethod method = new PostMethod(grouperWsRestUrl);
		        method.setRequestHeader("Connection", "close");

		        if (grouperRequestBean != null){
		        	// Encode the request and send it off
		        	String requestDocument = GrouperJsonUtil.toJSONString(grouperRequestBean);
		        	method.setRequestEntity(new StringRequestEntity(requestDocument, "text/x-json", "UTF-8"));
		        }

		    	int responseCode = client.executeMethod(method);
		    	log.info("POST to {} : {}", grouperWsRestUrl, responseCode);
		    	String responseString = IOUtils.toString(method.getResponseBodyAsStream());
		    	response = JSONObject.fromObject(responseString);
		    	log.trace(responseString);
		    }
		    return response;
		}
		catch (Exception e) {
			throw new GrouperException(e.getMessage());
		}
	}

}
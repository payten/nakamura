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
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.api.user;

import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_TITLE_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_PSEUDO_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.SAKAI_CATEGORY;
import static org.sakaiproject.nakamura.api.user.UserConstants.SAKAI_EXCLUDE;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AuthorizableUtil {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(AuthorizableUtil.class);

  //list of authorizables to not index
  public static final Set<String> IGNORE_AUTHIDS = ImmutableSet.of(Group.EVERYONE, User.ANON_USER, User.ADMIN_USER, "owner", "system");

  /**
   * Lists the Groups this authorizable is a member of excluding everyone. Includes group that the member is indirectly a memberOf
   * @param au the authorizable
   * @param authorizableManager
   * @return list of unique groups the authorizable is a member of, including indirect and intermediage membership.
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  public static List<Authorizable> getUserFacingGroups(Authorizable au, AuthorizableManager authorizableManager) throws AccessDeniedException, StorageClientException {

    List<Authorizable> realGroups = new ArrayList<Authorizable>();
    for (Iterator<Group> memberOf = au.memberOf(authorizableManager); memberOf.hasNext();) {
      Authorizable group = memberOf.next();
      if (group.isGroup() && isUserFacing(group, false)) {
        realGroups.add(group);
      }
    }
    return realGroups;
  }

  /**
   * Validates if an authorizable is a valid group
   * @param auth the authorizable
   * @param includeCollections Whether to include collections as user facing.
   * @return true if its an absolute group
   */
  public static boolean isUserFacing(Authorizable auth, boolean includeCollections) {
    // we don't want to count certrain special users & groups
    if (auth == null || IGNORE_AUTHIDS.contains(auth.getId())) {
      return false;
    }

    if (auth.isGroup()) {
      boolean isContactGroup = isContactGroup(auth);

      // don't count if the group is to be excluded
      boolean includeGroup = includeCollections
          && "collection".equals(String.valueOf(auth.getProperty(SAKAI_CATEGORY)))
          && "false".equals(String.valueOf(auth.getProperty(UserConstants.PROP_PSEUDO_GROUP)));
      boolean hasExclude = Boolean.parseBoolean(String.valueOf(auth.getProperty(SAKAI_EXCLUDE)));

      if (!includeGroup && !hasExclude) {
        includeGroup = true;
      }

      // don't count if the group lacks a title
      boolean lacksTitle = auth.getProperty(GROUP_TITLE_PROPERTY) == null
          || StringUtils.isEmpty(String.valueOf(auth.getProperty(GROUP_TITLE_PROPERTY)));

      if (isContactGroup || !includeGroup || lacksTitle) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validates that an Authorizable is not null, is a <code>Group</code> but is not a
   * contact group (i.e. title doesn't start with "g-contacts-"
   * 
   * @param group
   * @return
   */
  public static boolean isContactGroup(Authorizable group) {
    boolean result = false;
    if (group != null && group.isGroup() && group.getId().startsWith("g-contacts-")) {
      result = true;
    }
    return result;
  }

  /**
   * Check if an authorizable is a content collection.
   *
   * @param group
   * @param topLevel
   * @return
   */
  public static boolean isCollection(Authorizable group, boolean topLevel) {
    boolean retval = false;
    if (group != null && group.isGroup()
        && "collection".equals(group.getProperty(SAKAI_CATEGORY))) {
      retval = true;
    }

    if (topLevel) {
      retval = retval && "false".equals(String.valueOf(group.getProperty(PROP_PSEUDO_GROUP)));
    }
    return retval;
  }

  /**
   * @return true if the authz group is joinable
   */
  public static UserConstants.Joinable getJoinable(Authorizable authorizable, AuthorizableManager authorizableManager)
      throws StorageClientException, AccessDeniedException {

    if (authorizable instanceof Group) {
      Group targetGroup = (Group) authorizable;

      // if it's a pseudogroup, get joinability from its parent group instead
      Object pseudoGroupProp = authorizable.getProperty(UserConstants.PROP_PSEUDO_GROUP);
      if (pseudoGroupProp != null) {
        if (Boolean.parseBoolean(String.valueOf(pseudoGroupProp))) {
          String parentGroupID = String.valueOf(targetGroup.getProperty(UserConstants.PROP_PARENT_GROUP_ID));
          Authorizable parentGroup = authorizableManager.findAuthorizable(parentGroupID);
          if (parentGroup != null && parentGroup instanceof Group) {
            LOGGER.info("{} is a pseudoGroup, using its parent group {} for joinable property", authorizable, parentGroup);
            targetGroup = (Group) parentGroup;
          }
        }
      }

      if (targetGroup.hasProperty(UserConstants.PROP_JOINABLE_GROUP)) {
        try {
          String joinable = String.valueOf(targetGroup.getProperty(UserConstants.PROP_JOINABLE_GROUP));
          LOGGER.info("Joinable Property on {} {} ", targetGroup, joinable);
          if (joinable != null && !joinable.equals("null")) {
            return UserConstants.Joinable.valueOf(joinable);
          }
        } catch (IllegalArgumentException e) {
          LOGGER.info(e.getMessage(), e);
        }
      } else {
        LOGGER.info("No Joinable Property on {} ", targetGroup);
      }
    }
    return UserConstants.Joinable.no;
  }

}

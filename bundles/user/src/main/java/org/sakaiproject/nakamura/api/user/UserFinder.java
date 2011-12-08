package org.sakaiproject.nakamura.api.user;

import java.util.Set;

public interface UserFinder {
  
  /**
   * 
   * @param name
   * @return Set of userIds
   * @throws Exception
   */
  Set<String> findUsersByName(String name) throws Exception;
  
  /**
   * 
   * @param name
   * @return true if one or more users by that name found
   * false if not
   * @throws Exception
   */
  boolean userExists(String name) throws Exception;
}

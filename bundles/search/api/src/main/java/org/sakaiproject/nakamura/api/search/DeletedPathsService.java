package org.sakaiproject.nakamura.api.search;

import java.util.List;

public interface DeletedPathsService {

  /**
   * Get a list of the paths that were deleted since the last Solr commit across all nodes
   * in the cluster. Escapes the paths to make sure they are safe for consumption in a
   * query.
   */
  List<String> getDeletedPaths();
}
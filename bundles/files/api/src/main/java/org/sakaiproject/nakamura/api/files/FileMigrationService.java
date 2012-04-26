package org.sakaiproject.nakamura.api.files;

import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
public interface FileMigrationService {

  public static final int CURRENT_SCHEMA_VERSION = 2;

  public static final String STRUCTURE_ZERO = FilesConstants.STRUCTURE_FIELD_STEM + "0";

  boolean fileContentNeedsMigration(Content content);

  void migrateFileContent(Content content);
  
  Content migrateSinglePage(Content documentContent, Content pageContent);

  boolean isPageNode(Content content, ContentManager contentManager)
      throws StorageClientException, AccessDeniedException;

}

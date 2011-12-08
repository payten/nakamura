/**
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
package org.sakaiproject.nakamura.migratecustommimetype;

import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.lite.content.InternalContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate=true, metatype=true)
@Service(value=PropertyMigrator.class)
public class ContentMimetypeMigrator implements PropertyMigrator {

  private static final Logger log = LoggerFactory.getLogger(ContentMimetypeMigrator.class);

  private static final String OLD_MIME_FIELD = "sakai:custom-mimetype";

  public boolean migrate(String rid, Map<String, Object> properties) {
      String contentMimetype = (String)properties.get(OLD_MIME_FIELD);
      if (contentMimetype != null){
    	  properties.put(InternalContent.MIMETYPE_FIELD, contentMimetype);
    	  properties.remove(OLD_MIME_FIELD);
          log.debug("Updated {} {} ",rid,contentMimetype);
          return true;
      }
      return false;
	}

}

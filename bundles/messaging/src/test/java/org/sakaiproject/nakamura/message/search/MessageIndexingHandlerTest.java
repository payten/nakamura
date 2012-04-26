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
package org.sakaiproject.nakamura.message.search;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Test the Message Indexing Handler
 */
@RunWith(MockitoJUnitRunner.class)
public class MessageIndexingHandlerTest {

  private final static String TEMP_PATH = "/DffdkDl/tmp_id12345678/content";
  
  @Mock
  protected RepositorySession repositorySession;

  /**
   * Verify that trying to index content within a temporary path is gracefully ignored.
   */
  @Test
  public void testIgnoreTempContent() {
    Event event = createEventWithTempPath();
    MessageIndexingHandler handler = new MessageIndexingHandler();
    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);
    Assert.assertTrue("Expected an empty collection of solr input documents.", documents.isEmpty());
  }
  
  private Event createEventWithTempPath() {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put(IndexingHandler.FIELD_PATH, TEMP_PATH);
    return new Event("topic", props);
  }
}

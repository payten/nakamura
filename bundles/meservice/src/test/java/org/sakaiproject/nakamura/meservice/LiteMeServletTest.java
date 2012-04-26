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
package org.sakaiproject.nakamura.meservice;
import static junit.framework.Assert.assertEquals;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucketService;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.meservice.LiteMeServlet;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteMeServletTest {
  LiteMeServlet meServlet;
  @Mock
  LiteMessagingService messagingService;

  @Mock
  ConnectionManager connectionManager;

  @Mock
  MessageBucketService messageBucketService;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  BasicUserInfoService basicUserInfoService;

  @Before
  public void setUp() {
    meServlet = new LiteMeServlet();
    meServlet.messagingService = messagingService;
    meServlet.connectionManager = connectionManager;
    meServlet.messageBucketService = messageBucketService;
    meServlet.searchServiceFactory = searchServiceFactory;
    meServlet.basicUserInfoService = basicUserInfoService;
    
    meServlet.activate(Collections.emptyMap());
  }

  @Test
  public void getDefaultLocale() {
    Locale locale = meServlet.getLocale(Collections.<String, Object>emptyMap());
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }

  @Test
  public void getCustomLocale() {
    // common la_CO format
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", Locale.GERMANY.toString());
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMANY.getLanguage());
    assertEquals(locale.getCountry(), Locale.GERMANY.getCountry());

    // the funky format that prompted all this es_419
    props = ImmutableMap.<String, Object>of("locale", "es_419");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), "es");
    assertEquals(locale.getCountry(), "419");
  }

  @Test
  public void getCustomLanguage() {
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", Locale.GERMAN.toString());
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMAN.getLanguage());
    assertEquals(locale.getCountry(), "");
  }

  @Test
  public void getInvalidLocale() {
    // this is of bad form
    Map<String, Object> props = ImmutableMap.<String, Object>of("locale", "bad_WRONG");
    Locale locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of bad form
    props = ImmutableMap.<String, Object>of("locale", "123_456");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of valid form but not a real locale
    props = ImmutableMap.<String, Object>of("locale", "xx_XX");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), "xx");
    assertEquals(locale.getCountry(), "XX");

    // this is just weird
    props = ImmutableMap.<String, Object>of("locale", "_");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is jibberish
    props = ImmutableMap.<String, Object>of("locale", "jibberish");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is for utf8 testing
    props = ImmutableMap.<String, Object>of("locale", "ŠšĐđČčĆćŽž");
    locale = meServlet.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }
}

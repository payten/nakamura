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
package org.sakaiproject.nakamura.auth.saml;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ComparatorsTest {
  @Test
  public void testTrue() {
    assertTrue(SamlAuthenticationHandler.TRUE_URI_COMPARATOR.compare("same", "same"));
    assertTrue(SamlAuthenticationHandler.TRUE_URI_COMPARATOR.compare("doesn't matter", "whatever"));
    assertTrue(SamlAuthenticationHandler.TRUE_URI_COMPARATOR.compare(null, "whatever"));
    assertTrue(SamlAuthenticationHandler.TRUE_URI_COMPARATOR.compare(null, null));
  }

  @Test
  public void testHostOnly() {
    assertTrue(SamlAuthenticationHandler.HOST_ONLY_URI_COMPARATOR.compare("http://somehost:8080/same", "http://somehost:8080/same"));
    assertTrue(SamlAuthenticationHandler.HOST_ONLY_URI_COMPARATOR.compare("http://somehost:8080/same", "http://somehost:8080/different"));
    assertTrue(SamlAuthenticationHandler.HOST_ONLY_URI_COMPARATOR.compare("https://somehost:8080/same", "ftp://somehost:8080/different"));
    assertTrue(SamlAuthenticationHandler.HOST_ONLY_URI_COMPARATOR.compare("http://somehost:8080/same?whatsThis", "http://somehost:8080/different?dontKnow"));
  }

  @Test
  public void testSimple() {
    assertTrue(SamlAuthenticationHandler.SIMPLE_URI_COMPARATOR.compare("http://somehost:8080/same", "http://somehost:8080/same"));
    assertTrue(SamlAuthenticationHandler.SIMPLE_URI_COMPARATOR.compare("http://somehost:8080/same", "http://somehost:8080/same?stuff"));
    assertTrue(SamlAuthenticationHandler.SIMPLE_URI_COMPARATOR.compare("http://somehost:8080/same?this", "http://somehost:8080/same?that"));
    assertFalse(SamlAuthenticationHandler.SIMPLE_URI_COMPARATOR.compare("http://somehost:8080/same", "http://somehost:8080/different"));
    assertFalse(SamlAuthenticationHandler.SIMPLE_URI_COMPARATOR.compare("https://somehost:8080/same", "ftp://somehost:8080/different"));
  }
}

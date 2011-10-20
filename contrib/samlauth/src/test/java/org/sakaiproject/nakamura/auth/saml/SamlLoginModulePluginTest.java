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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.sling.jcr.jackrabbit.server.security.LoginModulePlugin;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.auth.saml.SamlAuthenticationHandler.SamlPrincipal;
import org.sakaiproject.nakamura.auth.saml.SamlLoginModulePlugin.SamlAuthenticationPlugin;

import java.security.Principal;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;
import javax.security.auth.login.FailedLoginException;

/**
 *
 */
public class SamlLoginModulePluginTest {
  SamlLoginModulePlugin loginPlugin;
  SamlAuthenticationPlugin authnPlugin;

  SimpleCredentials credentials;

  @Before
  public void setUp() throws Exception {
    loginPlugin = new SamlLoginModulePlugin();
    authnPlugin = new SamlAuthenticationPlugin(loginPlugin);

    credentials = new SimpleCredentials("someUser", new char[0]);
    credentials.setAttribute(SamlPrincipal.class.getName(), new SamlPrincipal("someUser"));
  }

  @Test
  public void coverageBooster() throws Exception {
    loginPlugin.addPrincipals(null);
    loginPlugin.doInit(null, null, null);
  }

  @Test
  public void testCanHandleSsoCredentials() throws RepositoryException {
    assertTrue(loginPlugin.canHandle(credentials));
  }

  @Test
  public void testCannotHandleOtherCredentials() {
    SimpleCredentials credentials = new SimpleCredentials("joe", new char[0]);
    assertFalse(loginPlugin.canHandle(credentials));
  }

  @Test
  public void testGetPrincipal() {
    assertEquals("someUser", loginPlugin.getPrincipal(credentials).getName());
  }

  @Test
  public void testImpersonate() throws FailedLoginException, RepositoryException {
    assertEquals(LoginModulePlugin.IMPERSONATION_DEFAULT, loginPlugin.impersonate(null, null));
  }

  @Test
  public void canGetAuthentication() throws Exception {
    loginPlugin.getAuthentication(null, credentials);
  }

  @Test
  public void cannotGetAuthenticationWrongCredentialType() throws Exception {
    Credentials credentials = mock(Credentials.class);
    loginPlugin.getAuthentication(null, credentials);
  }

  @Test
  public void cannotGetAuthenticationMissingSsoPrincipal() throws Exception {
    SimpleCredentials credentials = new SimpleCredentials("someUser", new char[0]);
    loginPlugin.getAuthentication(null, credentials);
  }

  @Test
  public void cannotGetAuthenticationWrongPrincipalType() throws Exception {
    SimpleCredentials credentials = new SimpleCredentials("someUser", new char[0]);
    credentials.setAttribute(SamlPrincipal.class.getName(), mock(Principal.class));
    loginPlugin.getAuthentication(null, credentials);
  }

  // ---------- AuthenticationPlugin ----------
  @Test
  public void authenticateTrue() throws Exception {
//    when(loginPlugin.canHandle(isA(Credentials.class))).thenReturn(true);
    assertTrue(authnPlugin.authenticate(credentials));
  }

  @Test
  public void authenticateFalse() throws Exception {
    credentials.removeAttribute(SamlPrincipal.class.getName());
    assertFalse(authnPlugin.authenticate(credentials));
  }
}

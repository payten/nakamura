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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.jackrabbit.server.security.AuthenticationPlugin;
import org.apache.sling.jcr.jackrabbit.server.security.LoginModulePlugin;
import org.sakaiproject.nakamura.auth.saml.SamlAuthenticationHandler.SamlPrincipal;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

/**
 * Plugin for interacting with the JCR authentication cycle.
 */
@Component
@Service
public class SamlLoginModulePlugin implements LoginModulePlugin {

  @SuppressWarnings("rawtypes")
  public void addPrincipals(Set principals) {
  }

  public boolean canHandle(Credentials credentials) {
    return (getPrincipal(credentials) != null);
  }

  @SuppressWarnings("rawtypes")
  public void doInit(CallbackHandler callbackHandler, Session session, Map options)
      throws LoginException {
  }

  public AuthenticationPlugin getAuthentication(Principal principal,
      Credentials credentials) throws RepositoryException {
    AuthenticationPlugin plugin = null;
    if (canHandle(credentials)) {
      plugin = new SamlAuthenticationPlugin(this);
    }
    return plugin;
  }

  public Principal getPrincipal(Credentials credentials) {
    SamlPrincipal ssoPrincipal = null;
    if (credentials instanceof SimpleCredentials) {
      SimpleCredentials simpleCredentials = (SimpleCredentials) credentials;
      Object attribute = simpleCredentials.getAttribute(SamlPrincipal.class.getName());
      if (attribute instanceof SamlPrincipal) {
        ssoPrincipal = (SamlPrincipal) attribute;
      }
    }
    return ssoPrincipal;
  }

  public int impersonate(Principal principal, Credentials credentials)
      throws RepositoryException, FailedLoginException {
    return LoginModulePlugin.IMPERSONATION_DEFAULT;
  }

  /**
   * Authentication plugin used during the JCR authentication cycle. Is returned by
   * {@link SamlLoginModulePlugin#getAuthentication(java.security.Principal, Credentials) to
   * be injected into the authentication cycle.
   */
  public static class SamlAuthenticationPlugin implements AuthenticationPlugin {
    private SamlLoginModulePlugin loginModulePlugin;

    public SamlAuthenticationPlugin(SamlLoginModulePlugin loginModulePlugin) {
      this.loginModulePlugin = loginModulePlugin;
    }

    /**
     * No actual authentication takes place here. By now, the CAS supplied credentials
     * will have been extracted and verified, and a getPrincipal() implementation will
     * have handled any necessary translation from the CAS principal name to a
     * Sling-appropriate principal. This only validates that the credentials
     * actually did come from the CAS handler.
     *
     * @see org.apache.sling.jcr.jackrabbit.server.security.AuthenticationPlugin#authenticate(javax.jcr.Credentials)
     */
    public boolean authenticate(Credentials credentials) throws RepositoryException {
      if (loginModulePlugin.canHandle(credentials)) {
        return true;
      } else {
        return false;
      }
    }
  }
}

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
package org.sakaiproject.nakamura.user.lite.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidator;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidatorService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserFinder;
import org.sakaiproject.nakamura.user.lite.resource.RepositoryHelper;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LiteCreateSakaiUserServletTest  {

  private RequestTrustValidatorService requestTrustValidatorService;
  @Mock
  private SlingHttpServletRequest request;
  
  @Mock
  private ResourceResolver resourceResolver;
  
  @Mock
  private LiteAuthorizablePostProcessService liteAuthorizablePostProcessService;
  
  @Mock
  private UserFinder userFinder;

  private Repository repository;
  
  private Session session;
  private LiteCreateSakaiUserServlet servlet;



  public LiteCreateSakaiUserServletTest() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    repository = RepositoryHelper.getRepository(new String[]{ "ieb","jeff","joe"}, new String[]{"g-course101", } );
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void before() throws ClientPoolException, StorageClientException, AccessDeniedException {
    
    javax.jcr.Session jcrSession = Mockito.mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    session = repository.loginAdministrative("ieb");
    Mockito.when(((SessionAdaptable)jcrSession).getSession()).thenReturn(session);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(request.getRemoteUser()).thenReturn("ieb");
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    requestTrustValidatorService = new RequestTrustValidatorService() {

      public RequestTrustValidator getValidator(String name) {
        return new RequestTrustValidator() {

          public boolean isTrusted(HttpServletRequest request) {
            return true;
          }

          public int getLevel() {
            return RequestTrustValidator.CREATE_USER;
          }
        };
      }
    };
    
    
    servlet = new LiteCreateSakaiUserServlet();
    ComponentContext componentContext = Mockito.mock(ComponentContext.class);
    Hashtable<String, Object> props = new Hashtable<String, Object>();
    props.put("password.digest.algorithm", "sha1");
    props.put("servlet.post.dateFormats", new String[]{"yyyy-MM"});
    when(componentContext.getProperties()).thenReturn(props);
    servlet.activate(componentContext);
    servlet.requestTrustValidatorService = requestTrustValidatorService;
    servlet.postProcessorService = liteAuthorizablePostProcessService;
    servlet.userFinder = userFinder;
  }

  @Test
  public void testNoPrincipalName() throws AccessDeniedException, StorageClientException  {
    badNodeNameParam(null, "User name was not submitted");
  }

  @Test
  public void testBadPrefix() throws AccessDeniedException, StorageClientException  {
    badNodeNameParam("g-contacts-all", "'g-contacts-' is a reserved prefix.");
  }

  private void badNodeNameParam(String name, String exception) throws AccessDeniedException, StorageClientException  {

    
    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn(name);

    HtmlResponse response = new HtmlResponse();

    try {
      servlet.handleOperation(request, response, null);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(exception, e.getMessage());
    }
  }

  @Test
  public void testNoPwd() throws StorageClientException, AccessDeniedException  {
    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn(null);

    HtmlResponse response = new HtmlResponse();

    try {
      servlet.handleOperation(request, response, null);
      fail();
    } catch (IllegalArgumentException e) {
    
    }
  }

  @Test
  public void testNotPwdEqualsPwdConfirm() throws StorageClientException, AccessDeniedException  {

    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn("bar");
    when(request.getParameter("pwdConfirm")).thenReturn("baz");

    HtmlResponse response = new HtmlResponse();
    try {
      servlet.handleOperation(request, response, null);
      fail();
    } catch (IllegalArgumentException e) {
    
    }
  }
  
  
  @Test
  public void testRequestTrusted() throws Exception  {

    
    when(request.getParameter(":create-auth")).thenReturn("typeA");
    servlet.repository = repository;
    servlet.eventAdmin = Mockito.mock(EventAdmin.class);
    servlet.requestTrustValidatorService = new RequestTrustValidatorService() {
      
      public RequestTrustValidator getValidator(String name) {
        if ( "typeA".equals(name)) {
          return new RequestTrustValidator() {
            
            public boolean isTrusted(HttpServletRequest request) {
              return true;
            }
            
            public int getLevel() {
              return CREATE_USER;
            }
          };
        }
        return null;
      }
    };
    
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn("bar");
    when(request.getParameter("pwdConfirm")).thenReturn("bar");
    Vector<String> paramNames= new Vector<String>();
    paramNames.add(SlingPostConstants.RP_NODE_NAME);
    paramNames.add("pwd");
    paramNames.add("pwdConfirm");
    when(request.getParameterNames()).thenReturn(paramNames.elements());
    
    RequestParameterMap requestParameterMap = Mockito.mock(RequestParameterMap.class);
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    when(request.getParameterValues(SlingPostConstants.RP_NODE_NAME)).thenReturn(new String[]{"foo"});
    when(request.getParameterValues("pwd")).thenReturn(new String[] {"bar"});
    when(request.getParameterValues("pwdConfirm")).thenReturn(new String[] {"bar"});
    when(userFinder.userExists("foo")).thenReturn(false);

    HtmlResponse response = new HtmlResponse();

    List<Modification> changes = Lists.newArrayList();
    servlet.handleOperation(request, response, changes);
    int responseCode = response.getStatusCode();
    assertEquals("if user does not already xist, expected response code"
        + HttpServletResponse.SC_CREATED, HttpServletResponse.SC_CREATED, responseCode);
  }
  
  @Test
  public void testUserExists() throws Exception  {
    when(request.getParameter(":create-auth")).thenReturn("typeA");
    servlet.repository = repository;
    servlet.eventAdmin = Mockito.mock(EventAdmin.class);
    servlet.requestTrustValidatorService = new RequestTrustValidatorService() {
      
      public RequestTrustValidator getValidator(String name) {
        if ( "typeA".equals(name)) {
          return new RequestTrustValidator() {
            
            public boolean isTrusted(HttpServletRequest request) {
              return true;
            }
            
            public int getLevel() {
              return CREATE_USER;
            }
          };
        }
        return null;
      }
    };
    
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn("bar");
    when(request.getParameter("pwdConfirm")).thenReturn("bar");
    Vector<String> paramNames= new Vector<String>();
    paramNames.add(SlingPostConstants.RP_NODE_NAME);
    paramNames.add("pwd");
    paramNames.add("pwdConfirm");
    when(request.getParameterNames()).thenReturn(paramNames.elements());
    
    RequestParameterMap requestParameterMap = Mockito.mock(RequestParameterMap.class);
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    when(request.getParameterValues(SlingPostConstants.RP_NODE_NAME)).thenReturn(new String[]{"foo"});
    when(request.getParameterValues("pwd")).thenReturn(new String[] {"bar"});
    when(request.getParameterValues("pwdConfirm")).thenReturn(new String[] {"bar"});
    when(userFinder.userExists("foo")).thenReturn(true);

    HtmlResponse response = new HtmlResponse();

    List<Modification> changes = Lists.newArrayList();
    servlet.handleOperation(request, response, changes);

    int responseCode = response.getStatusCode();
    assertEquals("if user already exists, expected response code"
        + HttpServletResponse.SC_BAD_REQUEST, HttpServletResponse.SC_BAD_REQUEST,
        responseCode);
  }
}

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
package org.sakaiproject.nakamura.auth.cas;

import static org.apache.sling.jcr.resource.JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS;

import com.ctc.wstx.stax.WstxInputFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.SimpleCredentials;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This class integrates SSO with the Sling authentication framework.
 * The integration is needed only due to limitations on servlet filter
 * support in the OSGi / Sling environment.
 */
@Component(metatype = true)
@Service({CasAuthenticationHandler.class, AuthenticationHandler.class})
@Properties(value = {
    @Property(name = Constants.SERVICE_RANKING, intValue = -5),
    @Property(name = AuthenticationHandler.PATH_PROPERTY, value = "/"),
    @Property(name = AuthenticationHandler.TYPE_PROPERTY, value = CasAuthenticationHandler.AUTH_TYPE, propertyPrivate = true),
    @Property(name = CasAuthenticationHandler.LOGIN_URL, value = CasAuthenticationHandler.DEFAULT_LOGIN_URL),
    @Property(name = CasAuthenticationHandler.LOGOUT_URL, value = CasAuthenticationHandler.DEFAULT_LOGOUT_URL),
    @Property(name = CasAuthenticationHandler.SERVER_URL, value = CasAuthenticationHandler.DEFAULT_SERVER_URL),
    @Property(name = CasAuthenticationHandler.RENEW, boolValue = CasAuthenticationHandler.DEFAULT_RENEW),
    @Property(name = CasAuthenticationHandler.GATEWAY, boolValue = CasAuthenticationHandler.DEFAULT_GATEWAY),
    @Property(name = CasAuthenticationHandler.PROXY, boolValue = CasAuthenticationHandler.DEFAULT_PROXY)
})
public class CasAuthenticationHandler implements AuthenticationHandler {

  public static final String AUTH_TYPE = "CAS";

  private static final Logger LOGGER = LoggerFactory
      .getLogger(CasAuthenticationHandler.class);

  static final String DEFAULT_ARTIFACT_NAME = "ticket";
  static final String DEFAULT_LOGIN_URL = "http://localhost/cas/login";
  static final String DEFAULT_LOGOUT_URL = "http://localhost/cas/logout";
  static final String DEFAULT_SERVER_URL = "http://localhost/cas";
  static final boolean DEFAULT_RENEW = false;
  static final boolean DEFAULT_GATEWAY = false;
  static final boolean DEFAULT_PROXY = false;

  @Reference
  Repository repo;

  /** Represents the constant for where the assertion will be located in memory. */
  static final String AUTHN_INFO = "org.sakaiproject.nakamura.auth.cas.SsoAuthnInfo";

  static final String LOGIN_URL = "sakai.auth.cas.url.login";
  private String loginUrl;

  static final String LOGOUT_URL = "sakai.auth.cas.url.logout";
  private String logoutUrl;

  static final String SERVER_URL = "sakai.auth.cas.url.server";
  private String serverUrl;

  static final String RENEW = "sakai.auth.cas.prop.renew";
  private boolean renew;

  static final String GATEWAY = "sakai.auth.cas.prop.gateway";
  private boolean gateway;

  static final String PROXY = "sakai.auth.cas.prop.proxy";
  private boolean proxy;

  // FIXME This will probably fail in a cluster, what if CAS calls back to a different
  // server
  protected static HashMap<String, String> pgtIOUs = new HashMap<String, String>();
  protected static HashMap<String, String> pgts = new HashMap<String, String>();

  /**
   * Define the set of authentication-related query parameters which should
   * be removed from the "service" URL sent to the SSO server.
   */
  Set<String> filteredQueryStrings = new HashSet<String>(Arrays.asList(
      REQUEST_LOGIN_PARAMETER, DEFAULT_ARTIFACT_NAME));

  public CasAuthenticationHandler() {
  }

  //----------- OSGi integration ----------------------------
  @Activate
  @Modified
  protected void modified(Map<?, ?> props) {
    loginUrl = PropertiesUtil.toString(props.get(LOGIN_URL), DEFAULT_LOGIN_URL);
    logoutUrl = PropertiesUtil.toString(props.get(LOGOUT_URL), DEFAULT_LOGOUT_URL);
    serverUrl = PropertiesUtil.toString(props.get(SERVER_URL), DEFAULT_SERVER_URL);

    renew = PropertiesUtil.toBoolean(props.get(RENEW), DEFAULT_RENEW);
    gateway = PropertiesUtil.toBoolean(props.get(GATEWAY), DEFAULT_GATEWAY);
    proxy = PropertiesUtil.toBoolean(props.get(PROXY), DEFAULT_PROXY);
  }

  //----------- AuthenticationHandler interface ----------------------------

  public void dropCredentials(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    String target = (String) request.getAttribute(Authenticator.LOGIN_RESOURCE);
    if (StringUtils.isBlank(target)) {
      target = request.getParameter(Authenticator.LOGIN_RESOURCE);
    }

    if (target != null && target.length() > 0 && !("/".equals(target))) {
      LOGGER.info(
          "SSO logout about to override requested redirect to {} and instead redirect to {}",
          target, logoutUrl);
    } else {
      LOGGER.debug("SSO logout will request redirect to {}", logoutUrl);
    }
    request.setAttribute(Authenticator.LOGIN_RESOURCE, logoutUrl);
  }

  public AuthenticationInfo extractCredentials(HttpServletRequest request,
      HttpServletResponse response) {
    LOGGER.trace("extractCredentials called");

    AuthenticationInfo authnInfo = null;

    String artifact = extractArtifact(request);

    if (artifact != null) {
      try {
        // make REST call to validate artifact
        String service = constructServiceParameter(request);

        String validateUrl = serverUrl + "/serviceValidate?service=" + service + "&ticket=" + artifact;
        if (proxy) {
          validateUrl =  validateUrl + "&pgtUrl=https%3A%2F%2F" + request.getServerName()
              + "%2Fsystem%2Fsling%2Fcas%2Fproxy";
        }

        GetMethod get = new GetMethod(validateUrl);
        HttpClient httpClient = new HttpClient();
        int returnCode = httpClient.executeMethod(get);

        if (returnCode >= 200 && returnCode < 300) {
          // successful call; test for valid response
          String body = get.getResponseBodyAsString();
          String credentials = retrieveCredentials(body);
          if (credentials != null) {
            // found some credentials; proceed
            authnInfo = createAuthnInfo(credentials);

            request.setAttribute(AUTHN_INFO, authnInfo);
          } else {
            LOGGER.warn("Unable to extract credentials from validation server.");
            authnInfo = AuthenticationInfo.FAIL_AUTH;
          }
        } else {
          LOGGER.error("Failed response from validation server: [{}]", returnCode);
          authnInfo = AuthenticationInfo.FAIL_AUTH;
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    return authnInfo;
  }

  /**
   * Called after extractCredentials has returned non-null but logging into the repository
   * with the provided AuthenticationInfo failed.<br/>
   * 
   * {@inheritDoc}
   * 
   * @see org.apache.sling.auth.core.spi.AuthenticationHandler#requestCredentials(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse)
   */
  public boolean requestCredentials(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    LOGGER.debug("requestCredentials called");

    final String service = constructServiceParameter(request);
    LOGGER.debug("Service URL = \"{}\"", service);
    final String urlToRedirectTo = constructLoginUrl(request, service);
    LOGGER.debug("Redirecting to: \"{}\"", urlToRedirectTo);
    response.sendRedirect(urlToRedirectTo);
    return true;
  }

  private String constructLoginUrl(HttpServletRequest request, String service) {
    ArrayList<String> params = new ArrayList<String>();

    String renewParam = request.getParameter("renew");
    boolean renew = this.renew;
    if (renewParam != null) {
      renew = Boolean.parseBoolean(renewParam);
    }
    if (renew) {
      params.add("renew=true");
    }

    String gatewayParam = request.getParameter("gateway");
    boolean gateway = this.gateway;
    if (gatewayParam != null) {
      gateway = Boolean.parseBoolean(gatewayParam);
    }
    if (gateway) {
      params.add("gateway=true");
    }

    params.add("service=" + service);
    return loginUrl + "?" + StringUtils.join(params, '&');
  }

  //----------- Internal ----------------------------
  private AuthenticationInfo createAuthnInfo(final String username) {
    final SsoPrincipal principal = new SsoPrincipal(username);
    AuthenticationInfo authnInfo = new AuthenticationInfo(AUTH_TYPE, username);
    SimpleCredentials credentials = new SimpleCredentials(principal.getName(),
        new char[] {});
    credentials.setAttribute(SsoPrincipal.class.getName(), principal);
    authnInfo.put(AUTHENTICATION_INFO_CREDENTIALS, credentials);
    return authnInfo;
  }

  /**
   * @param request
   * @return the URL to which the SSO server should redirect after successful
   * authentication. By default, this is the same URL from which authentication
   * was initiated (minus authentication-related query strings like "ticket").
   * A request attribute or parameter can be used to specify a different
   * return path.
   */
  protected String constructServiceParameter(HttpServletRequest request)
      throws UnsupportedEncodingException {
    StringBuffer url = request.getRequestURL().append("?");

    String queryString = request.getQueryString();
    String tryLogin = CasLoginServlet.TRY_LOGIN + "=2";
    if (queryString == null || queryString.indexOf(tryLogin) == -1) {
      url.append(tryLogin).append("&");
    }

    if (queryString != null) {
      String[] parameters = StringUtils.split(queryString, '&');
      for (String parameter : parameters) {
        String[] keyAndValue = StringUtils.split(parameter, "=", 2);
        String key = keyAndValue[0];
        if (!filteredQueryStrings.contains(key)) {
          url.append(parameter).append("&");
        }
      }
    }

    return URLEncoder.encode(url.toString(), "UTF-8");
  }

  private String extractArtifact(HttpServletRequest request) {
    return request.getParameter(DEFAULT_ARTIFACT_NAME);
  }

  private String retrieveCredentials(String responseBody) {
    String username = null;
    String pgtIou = null;
    String failureCode = null;
    String failureMessage = null;

    try {
      XMLInputFactory xmlInputFactory = new WstxInputFactory();
      xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
      xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);
      xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
      XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(new StringReader(
          responseBody));

      while (eventReader.hasNext()) {
        XMLEvent event = eventReader.nextEvent();

        // process the event if we're starting an element
        if (event.isStartElement()) {
          StartElement startEl = event.asStartElement();
          QName startElName = startEl.getName();
          String startElLocalName = startElName.getLocalPart();
          LOGGER.debug(responseBody);

          /*
           * Example of failure XML
          <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
            <cas:authenticationFailure code='INVALID_REQUEST'>
              &#039;service&#039; and &#039;ticket&#039; parameters are both required
            </cas:authenticationFailure>
          </cas:serviceResponse>
          */
          if ("authenticationFailure".equalsIgnoreCase(startElLocalName)) {
            // get code of the failure
            Attribute code = startEl.getAttributeByName(QName.valueOf("code"));
            failureCode = code.getValue();

            // get the message of the failure
            event = eventReader.nextEvent();
            assert event.isCharacters();
            Characters chars = event.asCharacters();
            failureMessage = chars.getData();
            break;
          }

          /*
           * Example of success XML
          <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
            <cas:authenticationSuccess>
              <cas:user>NetID</cas:user>
            </cas:authenticationSuccess>
          </cas:serviceResponse>
          */
          if ("authenticationSuccess".equalsIgnoreCase(startElLocalName)) {
            // skip to the user tag start
            while (eventReader.hasNext()) {
              event = eventReader.nextTag();
              if (event.isEndElement()) {
                if (eventReader.hasNext()) {
                  event = eventReader.nextTag();
                } else {
                  break;
                }
              }
              assert event.isStartElement();
              startEl = event.asStartElement();
              startElName = startEl.getName();
              startElLocalName = startElName.getLocalPart();
              if (proxy && "proxyGrantingTicket".equals(startElLocalName)) {
                event = eventReader.nextEvent();
                assert event.isCharacters();
                Characters chars = event.asCharacters();
                pgtIou = chars.getData();
                LOGGER.debug("XML parser found pgt: {}", pgtIou);
              } else if ("user".equals(startElLocalName)) {
                // move on to the body of the user tag
                event = eventReader.nextEvent();
                assert event.isCharacters();
                Characters chars = event.asCharacters();
                username = chars.getData();
                LOGGER.debug("XML parser found user: {}", username);
              } else {
                LOGGER.error(
                    "Found unexpected element [{}] while inside 'authenticationSuccess'",
                    startElName);
                break;
              }
              if (username != null && (!proxy || pgtIou != null)) {
                break;
              }
            }
          }
        }
      }
    } catch (XMLStreamException e) {
      LOGGER.error(e.getMessage(), e);
    }

    if (failureCode != null || failureMessage != null) {
      LOGGER.error("Error response from server code={} message={}", failureCode,
          failureMessage);
    }
    String pgt = pgts.get(pgtIou);
    if (pgt != null) {
      savePgt(username, pgt, pgtIou);
    } else {
      LOGGER.debug("Caching '{}' as the IOU for '{}'", pgtIou, username);
      pgtIOUs.put(pgtIou, username);
    }
    return username;
  }

  protected void savePgt(String username, String pgt, String pgtIou) {
    Map<String, Object> pgtId = new HashMap<String, Object>();
    pgtId.put("ticket", pgt);
    Session session = null;
    try {
      session = repo.loginAdministrative();
      ContentManager contentManager = session.getContentManager();
      if (contentManager.exists(LitePersonalUtils.getHomePath(username))) {
        String savePath = LitePersonalUtils.getPrivatePath(username) + "/cas";
        LOGGER.debug("Saving pgt '{}' to '{}'", pgtId, savePath);
        Content storedTicket = new Content(savePath, pgtId);
        contentManager.update(storedTicket);
        // remove the pgtIOU from cache
        pgts.remove(pgtIou);
        pgtIOUs.remove(pgtIou);
      } else {
        LOGGER.debug("User {} has no content home, not saving pgt", username);
      }
    } catch (StorageClientException e) {
      LOGGER.error("Couldn't save proxy granting ticket: ", e);
    } catch (AccessDeniedException e) {
      LOGGER.error("Permission error saving proxy granting ticket: ", e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          throw new RuntimeException("Failed to logout session", e);
        }
      }
    }
  }

  static final class SsoPrincipal implements Principal {
    private String principalName;

    public SsoPrincipal(String principalName) {
      this.principalName = principalName;
    }

    /**
     * {@inheritDoc}
     *
     * @see java.security.Principal#getName()
     */
    public String getName() {
      return principalName;
    }
  }

  protected String getUseridFromIOU(String pgtIou) {
    String userid = pgtIOUs.get(pgtIou);
    return userid;
  }

  protected String getProxyTicket(String pgt, String target) {
    String ticket = null;
    LOGGER.debug("Getting proxy ticket for service: '{}' with pgt: '{}'", target, pgt);
    String proxyTicketUrl = serverUrl + "/proxy?targetService=" + target + "&pgt=" + pgt;
    GetMethod get = new GetMethod(proxyTicketUrl);
    HttpClient httpClient = new HttpClient();
    int returnCode;
    try {
      returnCode = httpClient.executeMethod(get);
      if (returnCode >= 200 && returnCode < 300) {
        ticket = getProxyTicketFromXml(get.getResponseBodyAsString());
      }
    } catch (HttpException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return ticket;
  }

  private String getProxyTicketFromXml(String responseBody) {
    String ticket = null;

    try {
      XMLInputFactory xmlInputFactory = new WstxInputFactory();
      xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
      xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);
      xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
      XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(new StringReader(
          responseBody));
      LOGGER.debug(responseBody);

      while (eventReader.hasNext()) {
        XMLEvent event = eventReader.nextEvent();

        // process the event if we're starting an element
        if (event.isStartElement()) {
          StartElement startEl = event.asStartElement();
          QName startElName = startEl.getName();
          String startElLocalName = startElName.getLocalPart();

          // Example XML
          // <cas:serviceResponse>
          // <cas:proxySuccess>
          // <cas:proxyTicket>PT-957-ZuucXqTZ1YcJw81T3dxf</cas:proxyTicket>
          // </cas:proxySuccess>
          // </cas:serviceResponse>

          if ("proxySuccess".equalsIgnoreCase(startElLocalName)) {
            event = eventReader.nextTag();
            assert event.isStartElement();
            startEl = event.asStartElement();
            startElName = startEl.getName();
            startElLocalName = startElName.getLocalPart();
            if ("proxyTicket".equalsIgnoreCase(startElLocalName)) {
              event = eventReader.nextEvent();
              assert event.isCharacters();
              Characters chars = event.asCharacters();
              ticket = chars.getData();
            } else {
              LOGGER.error("Found unexpected element [{}] while inside 'proxySuccess'",
                  startElName);
              break;
            }
          }
        }
      }
    } catch (XMLStreamException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return ticket;
  }

  public void setpgt(String pgtIou, String pgt) {
    pgts.put(pgtIou, pgt);
  }
}

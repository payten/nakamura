package org.sakaiproject.nakamura.auth.saml;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.localserver.LocalTestServer;
import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.DateUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.SimpleCredentials;
import javax.jcr.ValueFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class SamlAuthenticationHandlerTest {
  SamlAuthenticationHandler ssoAuthenticationHandler;
  SimpleCredentials ssoCredentials;

  static final String ARTIFACT = "some-great-token-id";

  @Mock
  HttpServletRequest request;
  @Mock
  HttpServletResponse response;
  @Mock
  ValueFactory valueFactory;
  @Mock
  Repository repository;
  @Mock
  Session adminSession;
  @Mock
  AuthorizableManager authMgr;
  @Mock
  LiteAuthorizablePostProcessService authzPostProcessService;

  LocalTestServer server;
  HashMap<String, Object> props = new HashMap<String, Object>();

  @Before
  public void setUp() throws Exception {
    props.put(SamlAuthenticationHandler.SERVER_URL, "http://localhost/sso");
    props.put(SamlAuthenticationHandler.LOGOUT_URL, "http://localhost/logout");
    props.put(SamlAuthenticationHandler.MATCH_DESTINATION, SamlAuthenticationHandler.MATCH_DESTINATION_NONE);

    when(request.getScheme()).thenReturn("http");
    when(request.getServerName()).thenReturn("localhost");
    when(request.getRequestURI()).thenReturn("/login");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/login"));
    when(request.getMethod()).thenReturn("POST");

    ssoAuthenticationHandler = new SamlAuthenticationHandler(repository,
        authzPostProcessService);
    ssoAuthenticationHandler.activate(props);

    when(adminSession.getAuthorizableManager()).thenReturn(authMgr);
    when(repository.loginAdministrative()).thenReturn(adminSession);
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void coverageBooster() throws Exception {
    SamlAuthenticationHandler handler = new SamlAuthenticationHandler();
    handler.authenticationFailed(request, response, null);
  }

  @Test(expected = CertificateException.class)
  public void badCertificate() throws Exception {
    props.put(SamlAuthenticationHandler.CERTIFICATE, "/file/doesnt/exist");
    ssoAuthenticationHandler.activate(props);
    fail("Should've failed with bad certificate.");
  }

  @Test(expected = CertificateException.class)
  public void badCertificateFile() throws Exception {
    File tmpCert = File.createTempFile("samlbadtestfile-", ".cer");
    tmpCert.deleteOnExit();
    props.put(SamlAuthenticationHandler.CERTIFICATE, tmpCert.getPath());
    ssoAuthenticationHandler.activate(props);
    fail("Should've failed with bad certificate file.");
  }

  @Ignore
  public void goodCertificate() throws Exception {
//    String x509 = "some x509 string";
//    props.put(SamlAuthenticationHandler.CERTIFICATE, x509);
//    ssoAuthenticationHandler.activate(props);
//    fail("Should've failed with bad certificate file.");
  }

  @Ignore
  public void goodCertificateFile() throws Exception {
//    File tmpCert = File.createTempFile("samlbadtestfile-", ".cer");
//    tmpCert.deleteOnExit();
//    props.put(SamlAuthenticationHandler.CERTIFICATE, tmpCert.getPath());
//    ssoAuthenticationHandler.activate(props);
//    fail("Should've failed with bad certificate file.");
  }

  @Test
  public void authenticateNoTicket() throws Exception {
    assertNull(ssoAuthenticationHandler.extractCredentials(request, response));
  }

  @Test
  public void dropNoSession() throws IOException {
    ssoAuthenticationHandler.dropCredentials(request, response);
  }

  @Test
  public void dropCredentialsNoAssertion() throws IOException {
    ssoAuthenticationHandler.dropCredentials(request, response);
  }

  @Test
  public void dropCredentialsWithAssertion() throws IOException {
    ssoAuthenticationHandler.dropCredentials(request, response);
  }

  @Test
  public void dropCredentialsWithLogoutUrl() throws IOException {
    ssoAuthenticationHandler.dropCredentials(request, response);

    verify(request).setAttribute(Authenticator.LOGIN_RESOURCE, "http://localhost/logout");
  }

  @Test
  public void dropCredentialsWithRedirectTarget() throws IOException {
    when(request.getAttribute(Authenticator.LOGIN_RESOURCE)).thenReturn("goHere");

    ssoAuthenticationHandler.dropCredentials(request, response);

    verify(request).setAttribute(Authenticator.LOGIN_RESOURCE, "http://localhost/logout");
  }

  @Test
  public void extractCredentialsNoAssertion() throws Exception {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost"));
    when(request.getQueryString()).thenReturn("resource=/dev/index.html");

    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);

    assertNull(authenticationInfo);

    verify(request, never()).setAttribute(eq(SamlAuthenticationHandler.AUTHN_INFO),
        isA(AuthenticationInfo.class));
  }

  @Test
  public void extractCredentialsFromAssertion() throws Exception {
    setUpSsoCredentials();
    setupSamlResponse(request, "someUserId");

    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);

    assertNotNull(authenticationInfo);

    ssoCredentials = (SimpleCredentials) authenticationInfo
        .get(JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS);

    assertEquals("someUserId", authenticationInfo.getUser());
    assertEquals("someUserId", ssoCredentials.getUserID());

    verify(request).setAttribute(eq(SamlAuthenticationHandler.AUTHN_INFO),
        isA(AuthenticationInfo.class));
  }

  // AuthenticationFeedbackHandler tests.

  @SuppressWarnings("unchecked")
  @Test
  public void unknownUserNoCreation() throws Exception {
    setAutocreateUser(false);
    setUpSsoCredentials();
    setupSamlResponse(request, "someUserId");
    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);
    assertNotNull(authenticationInfo);
    boolean actionTaken = ssoAuthenticationHandler.authenticationSucceeded(request,
        response, authenticationInfo);
    assertFalse(actionTaken);
    verify(authMgr, never()).createUser(anyString(), anyString(), anyString(), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void findUnknownUserWithFailedCreation() throws Exception {
    setAutocreateUser(true);
    setupSamlResponse(request, "someUserId");
    when(authMgr.createUser(anyString(), anyString(), anyString(), any(Map.class))).thenReturn(Boolean.FALSE);
    setUpSsoCredentials();
    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);
    assertNotNull(authenticationInfo);
    boolean actionTaken = ssoAuthenticationHandler.authenticationSucceeded(request,
        response, authenticationInfo);
    assertFalse(actionTaken);
    verify(authMgr).createUser(eq("someUserId"), anyString(), anyString(), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void findKnownUserWithCreation() throws Exception {
    setAutocreateUser(true);
    setupSamlResponse(request, "someUserId");
    User liteUser = mock(User.class);
    when(liteUser.getId()).thenReturn("someUserId");
    when(authMgr.findAuthorizable("someUserId")).thenReturn(liteUser);
    setUpSsoCredentials();
    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);
    assertNotNull(authenticationInfo);
    boolean actionTaken = ssoAuthenticationHandler.authenticationSucceeded(request,
        response, authenticationInfo);
    assertFalse(actionTaken);
    verify(authMgr, never()).createUser(eq("someUserId"), anyString(), anyString(), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  private User setUpPseudoCreateUserService() throws Exception {
    User liteUser = mock(User.class);
    when(liteUser.getId()).thenReturn("someUserId");
    ItemBasedPrincipal principal = mock(ItemBasedPrincipal.class);
    when(principal.getPath()).thenReturn(UserConstants.USER_REPO_LOCATION + "/someUserIds");
    when(authMgr.createUser(eq("someUserId"), anyString(), anyString(), any(Map.class))).thenReturn(Boolean.TRUE);
    return liteUser;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void findUnknownUserWithCreation() throws Exception {
    setAutocreateUser(true);
    setUpSsoCredentials();
    setUpPseudoCreateUserService();
    setupSamlResponse(request, "someUserId");
    User liteUser = setUpPseudoCreateUserService();
    when(authMgr.findAuthorizable("someUserId")).thenReturn(null).thenReturn(liteUser);
    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);
    assertNotNull(authenticationInfo);
    boolean actionTaken = ssoAuthenticationHandler.authenticationSucceeded(request,
        response, authenticationInfo);
    assertFalse(actionTaken);
    verify(authMgr).createUser(eq("someUserId"), anyString(), anyString(), any(Map.class));
  }

  @Test
  public void postProcessingAfterUserCreation() throws Exception {
    LiteAuthorizablePostProcessService postProcessService = mock(LiteAuthorizablePostProcessService.class);
    ssoAuthenticationHandler.authzPostProcessService = postProcessService;
    setAutocreateUser(true);
    setUpSsoCredentials();
    setupSamlResponse(request, "someUserId");
    User liteUser = setUpPseudoCreateUserService();
    when(authMgr.findAuthorizable("someUserId")).thenReturn(null).thenReturn(liteUser);
    AuthenticationInfo authenticationInfo = ssoAuthenticationHandler.extractCredentials(
        request, response);
    assertNotNull(authenticationInfo);
    boolean actionTaken = ssoAuthenticationHandler.authenticationSucceeded(request,
        response, authenticationInfo);
    assertFalse(actionTaken);
    verify(postProcessService).process(any(Authorizable.class), any(Session.class),
        any(ModificationType.class), any(Map.class));
  }

  @Test
  public void requestCredentialsWithHandler() throws Exception {
    setUpSsoCredentials();
    assertTrue(ssoAuthenticationHandler.requestCredentials(request, response));
    verify(response).sendRedirect(isA(String.class));
  }

  // ---------- helper methods
  private void setUpSsoCredentials() throws Exception {
    setupValidateHandler();

    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost"));
    when(request.getQueryString()).thenReturn("resource=/dev/index.html");
  }

  private void setupValidateHandler() throws Exception {
    if (server == null) {
      server = new LocalTestServer(null, null);
      server.start();
    }

    String url = "http://" + server.getServiceHostName() + ":"
        + server.getServicePort() + "/sso"; // /identity/isTokenValid";
    props.put(SamlAuthenticationHandler.SERVER_URL, url);
    ssoAuthenticationHandler.activate(props);
  }

  private void setAutocreateUser(boolean bool) throws Exception {
    props.put(SamlAuthenticationHandler.SSO_AUTOCREATE_USER, bool);
    ssoAuthenticationHandler.activate(props);
  }

  private void setupSamlResponse(HttpServletRequest request, String username)
      throws UnsupportedEncodingException {
    // observed values used to mock date info
    //   date = 2011-06-03T03:28:28Z
    //   notOnOrAfter = 2011-06-03T03:38:28Z
    //   notBefore = 2011-06-03T03:18:28Z
    //   authInstant = 2011-06-03T02:55:08Z
    Calendar cal = Calendar.getInstance();
    String issueInstant = DateUtils.iso8601jcr(cal);
    cal.add(Calendar.MINUTE, 10);
    String notOnOrAfter = DateUtils.iso8601jcr(cal);
    cal.add(Calendar.MINUTE, -20);
    String notBefore = DateUtils.iso8601jcr(cal);
    cal.add(Calendar.HOUR, -1);
    cal.add(Calendar.MINUTE, 37);
    String authInstant = DateUtils.iso8601jcr(cal);
    when(request.getParameter("SAMLResponse"))
        .thenReturn(Base64.encodeBase64String(("<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\""
                + "  ID=\"1234567890199d3e4432f2e3ee888aa81234567890\" Version=\"2.0\""
                + "  IssueInstant=\"" + issueInstant + "\" Destination=\"http://localhost\">"
                + "  <saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">http://localhost:443/sso"
                + "  </saml:Issuer>"
                + "  <samlp:Status xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                + "    <samlp:StatusCode xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\""
                + "      Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\">"
                + "    </samlp:StatusCode>"
                + "  </samlp:Status>"
                + "  <saml:Assertion xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\""
                + "    ID=\"s2c281b29cb098964f714ba2b5af2c4e62e4308fdf\" IssueInstant=\"" + issueInstant + "\""
                + "    Version=\"2.0\">"
                + "    <saml:Issuer>http://localhost:443/sso</saml:Issuer>"
                + "    <Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "      <SignedInfo>"
                + "        <CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\" />"
                + "        <SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\" />"
                + "        <Reference URI=\"#s2c281b29cb098964f714ba2b5af2c4e62e4308fdf\">"
                + "          <Transforms>"
                + "            <Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\" />"
                + "            <Transform Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\" />"
                + "          </Transforms>"
                + "          <DigestMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\" />"
                + "          <DigestValue>Jd+mOxPL8nF8I0y0jtF5NThjPW8=</DigestValue>"
                + "        </Reference>"
                + "      </SignedInfo>"
                + "      <SignatureValue>"
                + "        l3KITJxpL0vI8t00lP9E7FsGJhR7QLRtp0AgHqU2eC+aFpLpJ0sgEQhEDGgLjXjL9iFWG32ixIxq"
                + "        kq2Q2cGcb38+LJyfqH1MPBOig4MTxuIgmMddQePQYLGKQoKb7LShfzUbcWnwLtiALA9ESGJ2z11C"
                + "        98XW2y+BbJ+zMB+MUp18C8YFL3EvimcpbopTJn+aciHqZdY76Ble4aNspVb18rM3YHZ0KhL1YKAH"
                + "        XGYhxFL8Zp1CbFycV3WSt+bOhJe7voePSSzwsfmvUj9CMzNrDi2vN3lRPhAXM8KLWSYfUwrtF/MX"
                + "        DZ9wrdfzxyXVUL38ecjN7fmowOv5JsUGiwUDTg=="
                + "      </SignatureValue>"
                + "      <KeyInfo>"
                + "        <X509Data>"
                + "          <X509Certificate>"
                + "            MIIDczCCAlugAwIBAgIBADANBgkqhkiG9w0BAQUFADB9MQswCQYDVQQGEwJVUzERMA8GA1UECBMI"
                + "            TmV3IFlvcmsxETAPBgNVBAcTCE5ldyBZb3JrMRwwGgYDVQQKExNOZXcgWW9yayBVbml2ZXJzaXR5"
                + "            MQwwCgYDVQQLEwNJVFMxHDAaBgNVBAMTE2RldnNzby5ob21lLm55dS5lZHUwHhcNMTAxMjE3MTcy"
                + "            MjQ0WhcNMjAxMjE0MTcyMjQ0WjB9MQswCQYDVQQGEwJVUzERMA8GA1UECBMITmV3IFlvcmsxETAP"
                + "            BgNVBAcTCE5ldyBZb3JrMRwwGgYDVQQKExNOZXcgWW9yayBVbml2ZXJzaXR5MQwwCgYDVQQLEwNJ"
                + "            VFMxHDAaBgNVBAMTE2RldnNzby5ob21lLm55dS5lZHUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw"
                + "            ggEKAoIBAQDWWOSbhnEiMmbYTHBOvt6NG+OceuKlzIt9vsEPE6aEoSlrXk6jIomvfpDmvWZ8BkBT"
                + "            tjzTyIH6KCzeexQ6Pb9s9RPBrPmd0RZDNgQuTjybj0ZGg/7eJKx1DDFUcvNa2CpHtULRv1j+DIrL"
                + "            4BaqvNJ1VWFT8hkeGaFpfUdAh3kje0Yv2i9ludBCts1K7KNQYEHNRgS34rHAA1LAXwFUbeRdUFP6"
                + "            PdMEsq08tKhoke7IZFJuHAvcP0AFQN9oBn8xe7Ny04yyqixtJJXQpC3EctCmhO/wUue0KLjYJX2P"
                + "            jbc7POs3at+MgFum8MuFocKQWqZu0I2bRWZBq16SLCeSXERTAgMBAAEwDQYJKoZIhvcNAQEFBQAD"
                + "            ggEBAM+7sYdWS+IZngk0JI0A1BiXB259Nud3pH5TmRZSN4tUCAi70Akezv1PdOK3PPmGVStDBXv+"
                + "            c4+R9BGySptU9tB2gq/HFed6KsL4icg8hxK87xK4bowo2Kc3dezMKEUcjcdv9iV2fd0zok6xvkeM"
                + "            wjLSBiNChVPlufy8takv4GMrkTaS5JuXmiL5uLVRQG7bmaIzQnqlCgWsEaDL6fVm3zSzdGicR9As"
                + "            ifzLZOOI5lYRpwT6tHn2GRtc3iAgwnH19cbZeUDzr8wq9PU6uys2SCCeGKTBb7kcpxkAJ15fJW90"
                + "            o867SybVjAOoVBqPWfKL5dgXfltws3rvGkclxswMUuo="
                + "          </X509Certificate>"
                + "        </X509Data>"
                + "      </KeyInfo>"
                + "    </Signature>"
                + "    <saml:Subject>"
                + "      <saml:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified\""
                + "        NameQualifier=\"http://localhost:443/sso\""
                + "        SPNameQualifier=\"http://localhost\">" + username + "</saml:NameID>"
                + "      <saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
                + "        <saml:SubjectConfirmationData"
                + "          NotOnOrAfter=\"" + notOnOrAfter + "\""
                + "          Recipient=\"http://localhost/login\" />"
                + "      </saml:SubjectConfirmation>"
                + "    </saml:Subject>"
                + "    <saml:Conditions NotBefore=\"" + notBefore + "\""
                + "      NotOnOrAfter=\"" + notOnOrAfter + "\">"
                + "      <saml:AudienceRestriction>"
                + "        <saml:Audience>http://localhost/login</saml:Audience>"
                + "      </saml:AudienceRestriction>"
                + "    </saml:Conditions>"
                + "    <saml:AuthnStatement AuthnInstant=\"" + authInstant + "\""
                + "      SessionIndex=\"s232fdc1026998c88ce6b96421943d0f7ee4f6e803\">"
                + "      <saml:AuthnContext>"
                + "        <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport"
                + "        </saml:AuthnContextClassRef>"
                + "      </saml:AuthnContext>"
                + "    </saml:AuthnStatement>"
                + "  </saml:Assertion>"
                + "</samlp:Response>").getBytes("UTF-8")));
  }
}

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
package org.sakaiproject.nakamura.http.usercontent;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionValidator;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionVeto;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * This implementation of the ServerProtectionService allows GETs and POSTs to the host,
 * provided they don't request bodies from areas not consider part of the application, and
 * provided the posts come with a referer header that is trusted.
 * </p>
 * <p>
 * GETs to application content (file bodies and encapsulated feeds) are allowed.
 * </p>
 * <p>
 * POSTS (or non GET/HEAD) operations from locations that are not trusted get a 400, bad
 * request.
 * </p>
 * <p>
 * GETs to non application content get redirected, with a HMAC SHA-512 digest to a host
 * that serves that content.
 * </p>
 * <p>
 * If the GET is an anon GET, then it gets redirected with no HMAC.
 * </p>
 * <p>
 * The default setup is http://localhost:8080 is the trusted server. http://localhost:8082
 * is the content server, referers of /* and http://localhost:8080* are trusted sources
 * of POST operations. Application data is any feed that does not stream a body, and
 * anything under /dev, /devwidgets, /index.html, all other GET operations are assumed to
 * be raw user content.
 * </p>
 * <p>
 * There is a distinct difference in what is required when calling each of the
 * <pre>is*Safe</pre> methods. Please check the javadoc on each method to see what
 * resource expectations there are.
 * </p>
 */
@Component(immediate = true, metatype = true)
@Service(value = ServerProtectionService.class)
public class ServerProtectionServiceImpl implements ServerProtectionService {
  private static final String HMAC_SHA512 = "HmacSHA512";
  private static final String HMAC_PARAM = ":hmac";
  private static final String[] DEFAULT_TRUSTED_HOSTS = { "localhost:8080 = http://localhost:8082" };
  private static final String[] DEFAULT_TRUSTED_PATHS = { "/dev", "/devwidgets", "/system", "/logout", "/var" };
  private static final String[] DEFAULT_TRUSTED_EXACT_PATHS = { };
  private static final String DEFAULT_TRUSTED_SECRET_VALUE = "This Must Be set in production";
  private static final String[] DEFAULT_WHITELIST_POST_PATHS = {"/system/console"};
  private static final String[] DEFAULT_ANON_WHITELIST_POST_PATHS = {"/system/userManager/user.create"};

  @Property(boolValue=false)
  private static final String DISABLE_XSS_PROTECTION_FOR_UI_DEV = "disable.protection.for.dev.mode";
  @Property(value = { "/dev", "/devwidgets", "/system", "/logout", "/var" })
  private static final String TRUSTED_PATHS_CONF = "trusted.paths";
  @Property(value = { })
  private static final String TRUSTED_EXACT_PATHS_CONF = "trusted.exact.paths";
  @Property(value = { "localhost:8080 = http://localhost:8082" }, cardinality = 9999999)
  protected static final String TRUSTED_HOSTS_CONF = "trusted.hosts";
  @Property(value = { DEFAULT_TRUSTED_SECRET_VALUE })
  private static final String TRUSTED_SECRET_CONF = "trusted.secret";
  @Property(value = {"/system/console"}, cardinality = 9999999)
  private static final String WHITELIST_POST_PATHS_CONF = "trusted.postwhitelist";
  @Property(value = {"/system/userManager/user.create", "/system/batch"})
  private static final String ANON_WHITELIST_POST_PATHS_CONF = "trusted.anonpostwhitelist";
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ServerProtectionServiceImpl.class);

  /**
   * Map of Host headers to content hosts.
   */
  private Map<String, String> applicationContentRedirects;
  /**
   * Map of Host headers to acceptable refereres. 
   */
  private Map<String, String> applicationReferrerHeaders;
  /**
   * List of path stems its safe to stream content bodies from using a trusted host
   */
  private String[] safeToStreamPaths;
  /**
   * List of path stems its safe to stream content bodies from using a trusted host
   */
  private Set<String> safeToStreamExactPaths;
  /**
   * Array of keys created from the secret, indexed by the second digit of the timestamp
   */
  private Key[] transferKeys;
  /**
   * List of url stems that are always Ok to accept posts from on any URL (eg
   * /system/console). You will want to add additional protection on these.
   */
  private String[] postWhiteList;
  /**
   * list of paths where its safe for anon to post to.
   */
  private String[] safeForAnonToPostPaths;

  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC, strategy = ReferenceStrategy.EVENT, bind = "bindServerProtectionValidator", unbind = "unbindServerProtectionValidator")
  private ServerProtectionValidator[] serverProtectionValidators = new ServerProtectionValidator[0];
  private Map<ServiceReference, ServerProtectionValidator> serverProtectionValidatorsStore = Maps
      .newConcurrentMap();
  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC, strategy = ReferenceStrategy.EVENT, bind = "bindServerProtectionVeto", unbind = "unbindServerProtectionVeto")
  private ServerProtectionVeto[] serverProtectionVetos = new ServerProtectionVeto[0];
  private Map<ServiceReference, ServerProtectionVeto> serverProtectionVetosStore = Maps
      .newConcurrentMap();

  private BundleContext bundleContext;
  private boolean disableProtectionForDevMode;

  @Modified
  public void modified(ComponentContext componentContext)
      throws NoSuchAlgorithmException, UnsupportedEncodingException,
      InvalidSyntaxException {
    @SuppressWarnings("unchecked")

    Dictionary<String, Object> properties = componentContext.getProperties();
    disableProtectionForDevMode = PropertiesUtil.toBoolean(properties.get(DISABLE_XSS_PROTECTION_FOR_UI_DEV), false);
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [modified]");
      return;
    }
    String[] trustedHosts = PropertiesUtil.toStringArray(
        properties.get(TRUSTED_HOSTS_CONF), DEFAULT_TRUSTED_HOSTS);
		Builder<String, String> redirects = ImmutableMap.builder();
		Builder<String, String> referrers = ImmutableMap.builder();
		for (String h : trustedHosts) {
			String[] applicationContentHostPair = StringUtils.split(h, "=", 2);
			if (applicationContentHostPair == null
					|| applicationContentHostPair.length != 2) {
				throw new IllegalArgumentException(
						"Application Content Host Pair invalid " + h);
			}
			applicationContentHostPair[0] = applicationContentHostPair[0]
					.trim();
			applicationContentHostPair[1] = applicationContentHostPair[1]
					.trim();
			String[] proto = StringUtils.split(applicationContentHostPair[1],
					":", 2);
      if (applicationContentHostPair[0].startsWith("http")) {
        throw new IllegalArgumentException("Trusted host must not start with protocol: "
            + applicationContentHostPair[0]);
      }
			if (proto == null || proto.length != 2) {
				throw new IllegalArgumentException(
						"Content Host Must contain protocol " + h);
			}
			String[] hostHeader = StringUtils.split(
					applicationContentHostPair[0], ":", 2);
			if (hostHeader == null || hostHeader.length == 0) {
				throw new IllegalArgumentException(
						"Application Host Must contain port " + h);
			}
			// we don't allow redirect onto a different protocol, which implies
			// the app is on the same protocol as
			// the content. Also this ensures that the trusted host does not
			// discriminate on protocol.
			LOGGER.info("Adding {} {} ", applicationContentHostPair[0],
					applicationContentHostPair[1]);
			redirects.put(applicationContentHostPair[0],
						applicationContentHostPair[1]);
			if ("http".equals(proto[0])) {
				if (hostHeader.length > 1 && "80".equals(hostHeader[1])) {
					referrers.put(applicationContentHostPair[0], "http://"
							+ hostHeader[0]);
				} else {
					referrers.put(applicationContentHostPair[0], "http://"
							+ applicationContentHostPair[0]);
				}
			} else if ("https".equals(proto[0])) {
				// requests on default ports will not have the port in the referrer.
				if (hostHeader.length > 1 && "443".equals(hostHeader[1])) {
					referrers.put(applicationContentHostPair[0], "https://"
							+ hostHeader[0]);
				} else {
					referrers.put(applicationContentHostPair[0], "https://"
							+ applicationContentHostPair[0]);
				}
			} else {
				LOGGER.warn("Protocol was not recognised {} {} ", proto[0], h);
				throw new IllegalArgumentException("Protocol not recognised "
						+ h);
			}
		}
		applicationContentRedirects = redirects.build();
		applicationReferrerHeaders = referrers.build();
    safeToStreamPaths = PropertiesUtil.toStringArray(properties.get(TRUSTED_PATHS_CONF),
        DEFAULT_TRUSTED_PATHS);
    safeToStreamExactPaths = ImmutableSet.copyOf(PropertiesUtil.toStringArray(
        properties.get(TRUSTED_EXACT_PATHS_CONF), DEFAULT_TRUSTED_EXACT_PATHS));
    postWhiteList = PropertiesUtil.toStringArray(
        properties.get(WHITELIST_POST_PATHS_CONF), DEFAULT_WHITELIST_POST_PATHS);
    safeForAnonToPostPaths = PropertiesUtil.toStringArray(
        properties.get(ANON_WHITELIST_POST_PATHS_CONF), DEFAULT_ANON_WHITELIST_POST_PATHS);
    String transferSharedSecret = PropertiesUtil.toString(properties.get(TRUSTED_SECRET_CONF),
        DEFAULT_TRUSTED_SECRET_VALUE);
    if (DEFAULT_TRUSTED_SECRET_VALUE.equals(transferSharedSecret)) {
      LOGGER.error("Configuration Error =============================");
      LOGGER
          .error("Configuration Error: Please set {} to secure Content Server in procuction ",TRUSTED_SECRET_CONF);
      LOGGER.error("Configuration Error =============================");
    }

    LOGGER.info("Trusted Hosts {}", applicationContentRedirects);
    LOGGER.info("Trusted Stream Paths {} ",Arrays.toString(safeToStreamPaths));
    LOGGER.info("Trusted Stream Resources {} ",safeToStreamExactPaths);
    LOGGER.info("POST Whitelist {} ",postWhiteList);
    LOGGER.info("Content Shared Secret [{}] ",transferSharedSecret);

    transferKeys = new Key[10];
    MessageDigest md = MessageDigest.getInstance("SHA-512");
    Base64 encoder = new Base64(true);
    byte[] input = transferSharedSecret.getBytes("UTF-8");
    // create a static ring of 10 keys by repeatedly hashing the last key seed
    // starting with the transferSharedSecret
    for (int i = 0; i < transferKeys.length; i++) {
      md.reset();
      byte[] data = md.digest(input);
      transferKeys[i] = new SecretKeySpec(data, HMAC_SHA512);
      input = encoder.encode(data);
    }

    bundleContext = componentContext.getBundleContext();
    ServiceReference[] srs = bundleContext.getAllServiceReferences(
        ServerProtectionValidator.class.getName(), null);
    if ( srs != null ) {
      for (ServiceReference sr : srs) {
        bindServerProtectionValidator(sr);
      }
    }
    ServiceReference[] srsVeto = bundleContext.getAllServiceReferences(
        ServerProtectionVeto.class.getName(), null);
    if ( srsVeto != null ) {
      for (ServiceReference sr : srsVeto) {
        bindServerProtectionVeto(sr);
      }
    }
  }
  @Activate
  public void activate(ComponentContext componentContext) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidSyntaxException {
    modified(componentContext);
  }

  @Deactivate
  public void destroy(ComponentContext c) {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [destroy]");
      return;
    }
    BundleContext bc = c.getBundleContext();
    for (Entry<ServiceReference, ServerProtectionValidator> e : serverProtectionValidatorsStore
        .entrySet()) {
      bc.ungetService(e.getKey());
    }
    serverProtectionValidatorsStore.clear();
    serverProtectionValidators = null;
  }

  /**
   * {@inheritDoc}
   * 
   * This method requires a resource to operate successfully and is best used by a Sling
   * Filter rather than a Servlet Filter, so that Sling will have made the appropriate
   * resource resolution.
   *
   * @see org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService#isRequestSafe(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  public boolean isRequestSafe(SlingHttpServletRequest srequest,
      SlingHttpServletResponse sresponse) throws UnsupportedEncodingException,
      IOException {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [isRequestSafe]");
      return true;
    }
    // if the method is not safe, the request can't be safe.
    if (!isMethodSafe(srequest, sresponse)) {
      return false;
    }
    String method = srequest.getMethod();
    if ( "GET|OPTIONS|HEAD".indexOf(method) < 0 ) {
      String userId = srequest.getRemoteUser();
      if ( User.ANON_USER.equals(userId) ) {
        String path = srequest.getRequestURI();
        boolean safeForAnonToPost = false;
        for (String safePath : safeForAnonToPostPaths) {
          if (path.startsWith(safePath)) {
            safeForAnonToPost = true;
            break;
          }
        }
        if ( ! safeForAnonToPost ) {
          sresponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Anon users may not perform POST operations");
          return false;
        }
      }
    }
    boolean safeHost = isSafeHost(srequest);
    if (safeHost && "GET".equals(method)) {
      boolean safeToStream = false;
      RequestPathInfo requestPathInfo = srequest.getRequestPathInfo();
      String ext = requestPathInfo.getExtension();
      if (ext == null || "res".equals(ext)) {
        // this is going to stream
        String path = srequest.getRequestURI();
        LOGGER.debug("Checking [{}] RequestPathInfo {}", path, requestPathInfo);
        safeToStream = isMatchToSafePath(path);
        if (!safeToStream) {
          Resource resource = srequest.getResource();
          if ( resource != null ) {
            if ("sling:nonexisting".equals(resource.getResourceType())) {
              // Trust a "GET" of non-existing content so that the 404 comes from
              // the right port (KERN-2001)
              LOGGER.debug("Non existing resource {}", resource.getPath());
              return true;
            }
            // The original unrecognized URI might be a mapping to a trusted resource.
            // Check again now that any aliases have been resolved.
            String resourcePath = resource.getPath();
            LOGGER.debug("Checking Resource Path [{}]",resourcePath);
            safeToStream = isMatchToSafePath(resourcePath);
            if (!safeToStream) {
              for (ServerProtectionValidator serverProtectionValidator : serverProtectionValidators) {
                if ( serverProtectionValidator.safeToStream(srequest, resource)) {
                  LOGGER.debug(" {} said this {} is safe to stream ",serverProtectionValidator,resourcePath);
                  safeToStream = true;
                  break;
                }
              }
            }
          }
        } else {
          LOGGER.debug("Content was safe to stream ");
        }
      } else {
        safeToStream = true;
        LOGGER.debug("doesnt look like a body, checking with vetos" );
      }
      LOGGER.debug("Checking server vetos, safe to stream ? {} ", safeToStream);
      for (ServerProtectionVeto serverProtectionVeto : serverProtectionVetos) {
        LOGGER.debug("Checking for Veto on {} ",serverProtectionVeto);
        if ( serverProtectionVeto.willVeto(srequest)) {
          safeToStream = serverProtectionVeto.safeToStream(srequest);
          LOGGER.debug("{} vetoed {}  ", serverProtectionVeto, safeToStream);
          break;
        }
      }
      if (!safeToStream) {
        redirectToContent(srequest, sresponse);
        return false;
      }
      LOGGER.debug("Request will be sent from this host, no redirect {}", srequest.getRequestURL().toString());
    }
    return true;
  }

  private boolean isMatchToSafePath(String path) {
    boolean safeToStream = safeToStreamExactPaths.contains(path);
    if (!safeToStream) {
      LOGGER.debug("Checking [{}] looks like not safe to stream ", path );
      for (String safePath : safeToStreamPaths) {
        if (path.startsWith(safePath)) {
          safeToStream = true;
          LOGGER.debug("Safe To stream becuase starts with {} ",safePath);
          break;
        }
      }
    }
    return safeToStream;
  }

  private void redirectToContent(HttpServletRequest request, HttpServletResponse response)
      throws UnsupportedEncodingException, IOException {
    StringBuffer requestURL = request.getRequestURL();
    String queryString = request.getQueryString();
    if (queryString != null) {
      requestURL.append("?").append(queryString);
    }
    String url = requestURL.toString();
    // replace the protocol and host with the CDN host.
    int pathStart = requestURL.indexOf("/", requestURL.indexOf(":") + 3);
    url = getTransferUrl(request, url.substring(pathStart));
    // send via the session establisher
    LOGGER.debug("Sending redirect for {} {} ",request.getMethod(), url);
    response.sendRedirect(url);
  }

  /**
   * @param request
   * @param urlPath
   * @return
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws IllegalStateException
   * @throws UnsupportedEncodingException
   */
  private String getTransferUrl(HttpServletRequest request, String urlPath) {
	  String trustedHostHeader = buildTrustedHostHeader(request);
	  if ( trustedHostHeader == null || !applicationContentRedirects.containsKey(trustedHostHeader) ) {
		  LOGGER.warn("No Content Host found for {} ", request.getRequestURL());
		  throw new IllegalArgumentException("No Content Host foudn for request, cant transfer ");
	  }
	  String redirectUrl = applicationContentRedirects.get(trustedHostHeader) + urlPath;
    // only transfer authN from a trusted safe host
    if (isSafeHost(request)) {
      String userId = request.getRemoteUser();
      if (userId != null && !User.ANON_USER.equals(userId)) {
        try {
          long ts = System.currentTimeMillis();
          int keyIndex = (int) (ts - ((ts / 10) * 10));
          Mac m = Mac.getInstance(HMAC_SHA512);
          m.init(transferKeys[keyIndex]);

          String message = createMessage(redirectUrl, userId, String.valueOf(ts));
          
          m.update(message.getBytes("UTF-8"));
          String hmac = Base64.encodeBase64URLSafeString(m.doFinal());
          hmac = Base64.encodeBase64URLSafeString((hmac + ";" + userId + ";" + ts)
              .getBytes("UTF-8"));
          String spacer = "?";
          if ( redirectUrl.indexOf('?') >  0) {
            spacer = "&";
          }
          redirectUrl = redirectUrl + spacer + HMAC_PARAM + "=" + hmac;
          LOGGER.debug("Message was [{}] ", message);
          LOGGER.debug("Key was [{}] [{}] ", keyIndex, transferKeys[keyIndex]);
          LOGGER.debug("Transfer URL created as [{}] ",redirectUrl);
        } catch (Exception e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
    return redirectUrl;
  }

  private String buildTrustedHostHeader(HttpServletRequest request) {
	  // try the host header first
	  String host = request.getHeader("Host");
	  if ( host != null && host.trim().length() > 0 ) {
		  return host;
	  }
	  // if not suitable resort to letting jetty build the host header
	  int port = request.getServerPort();
	  String scheme = request.getScheme();
	  String serverName = request.getServerName();
	  // default ports are not added to the header.
	  if ( (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))) {
		  return serverName; 
	  } else {
		  return serverName+":"+port;
	  }
  }
  private String createMessage(String url, String userId, String ts ) {
    // strip the protocol since it wont survive front end proxies, if those proxies re-write the protocol.
    if ( url.startsWith("http:") ) {
       url = url.substring(5);
    } else if ( url.startsWith("https:") ) {
       url = url.substring(6);
    }
    return url + ";" + userId + ";" + ts;
  }

  public String getTransferUserId(HttpServletRequest request) {
    // only ever get a user ID in this way on a non trusted safe host.
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [getTransferUserId]");
      return null;
    }
    // the host must not be safe to decode the user transfer UserID, and the method must be a GET or HEAD
    String method = request.getMethod();
    if (!isSafeHost(request) && ("GET".equals(method) || "HEAD".equals(method))) {
      String hmac = request.getParameter(HMAC_PARAM);
      if (hmac != null) {
        try {
          hmac = new String(Base64.decodeBase64(hmac.getBytes("UTF-8")), "UTF-8");
          String[] parts = StringUtils.split(hmac, ';');
          String requestUrl = request.getRequestURL().append("?")
              .append(request.getQueryString()).toString();
          LOGGER.debug("Checking requestUrl [{}", requestUrl);
          int i = requestUrl.indexOf("&" + HMAC_PARAM);
          if ( i < 0 ) {
            i = requestUrl.indexOf("?" + HMAC_PARAM);
          }
          String finalUrl = requestUrl.substring(0, i);
          String requestHmac = parts[0];
          String requestUserId = parts[1];
          String requestTs = parts[2];
          String message = createMessage(finalUrl, requestUserId, requestTs);
          long requestTsL = Long.parseLong(requestTs);
          if (Math.abs(System.currentTimeMillis() - requestTsL) < 60000L) {
            int keyIndex = (int) (requestTsL - ((requestTsL / 10) * 10));
            Mac m = Mac.getInstance(HMAC_SHA512);
            m.init(transferKeys[keyIndex]);
            m.update(message.getBytes("UTF-8"));
            String testHmac = Base64.encodeBase64URLSafeString(m.doFinal());
            if (testHmac.equals(requestHmac)) {
              LOGGER.debug("Successfully extracted requestUserId {} from HMAC", requestUserId);
              return requestUserId;
            } else {
              LOGGER.debug("Message was [{}] ", message);
              LOGGER.debug("Key was [{}] [{}] ", keyIndex, transferKeys[keyIndex]);
              LOGGER.debug("Hmac did not validate testHmac was [{}], requestHmac [{}] ", testHmac, requestHmac); 
            }
          } else {
            LOGGER.debug("Hmac has expired, older than 60s, hmac message was {} ", message);
          }
        } catch (Exception e) {
          LOGGER.warn(e.getMessage());
          LOGGER.debug(e.getMessage(), e);
        }
      }
    } else {
      LOGGER.debug("Request is to a safe host, wont look for a transfer of trust to this host. {} ",request.getRequestURL().toString());
    }
    return null;
  }

  /**
   * {@inheritDoc}
   * 
   * This method has no resource requirements to operate successfully allowing for it to
   * be called by a filter without any cost to resource resolution.
   * 
   * @see org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService#isMethodSafe(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse)
   */
  public boolean isMethodSafe(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [isMethodSafe]");
      return true;
    }
    String method = hrequest.getMethod();
    boolean safeHost = isSafeHost(hrequest);

    // protect against POST originating from other domains, this assumes that there is no
    // browser bug in this area
    // and no flash bug.
    if (!("GET".equals(method) || "HEAD".equals(method))) {
      String path = hrequest.getRequestURI();
      for (String okPostStem : postWhiteList) {
        if (path.startsWith(okPostStem)) {
          return true;
        }
      }
      // check the Referer
      @SuppressWarnings("unchecked")
      Enumeration<String> referers = hrequest.getHeaders("Referer");
      String referer = null;
      if (referers == null || !referers.hasMoreElements()) {
        LOGGER.debug("No Referer header present ");
        hresponse.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "POST Requests with no Referer are not acceptable");
        return false;
      }
      referer = referers.nextElement();
      if (referer == null) {
        LOGGER.debug("No Referer header present, was null ");
        hresponse.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "POST Requests with no Referer are not acceptable");
        return false;
      }

      // Do we allow non get operations to this host ?
      if (safeHost) {
        // and if we do, do we accept them from the Referer mentioned ?
				String safeReferer = applicationReferrerHeaders
						.get(buildTrustedHostHeader(hrequest));
				if (referer.startsWith("/")) {
					LOGGER.warn(
							"Referer header from test script, allowed safe:[{}] request:[{}] ",
							safeReferer, referer);
					safeHost = true;
				} else if (referer.startsWith(safeReferer)) {
					safeHost = true;
					LOGGER.debug("Accepted referred safe:[{}] request:[{}]", safeReferer,
							referer);
				} else {
					safeHost = false;
					LOGGER.debug("Rejecting referred safe:[{}] request:[{}]", safeReferer,
							referer);
				}
      }
      if (!safeHost) {
        hresponse
            .sendError(
                HttpServletResponse.SC_BAD_REQUEST,
                "POST Requests are only accepted from the Application, this request was not from the application.");
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   * 
   * This method has no resource requirements to operate successfully allowing for it to
   * be called by a filter without any cost to resource resolution.
   * 
   * @see org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService#isSafeHost(javax.servlet.http.HttpServletRequest)
   */
  public boolean isSafeHost(HttpServletRequest hrequest) {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled [isSafeHost]");
      return true;
    }    
    return applicationReferrerHeaders.containsKey(buildTrustedHostHeader(hrequest));
  }

  public void bindServerProtectionValidator(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionValidatorsStore.put(serviceReference,
          (ServerProtectionValidator) bundleContext.getService(serviceReference));
      serverProtectionValidators = serverProtectionValidatorsStore.values().toArray(
          new ServerProtectionValidator[serverProtectionValidatorsStore.size()]);
    }
  }

  public void unbindServerProtectionValidator(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionValidatorsStore.remove(serviceReference);
      bundleContext.ungetService(serviceReference);
      serverProtectionValidators = serverProtectionValidatorsStore.values().toArray(
          new ServerProtectionValidator[serverProtectionValidatorsStore.size()]);
    }
  }

  public void bindServerProtectionVeto(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionVetosStore.put(serviceReference,
          (ServerProtectionVeto) bundleContext.getService(serviceReference));
      serverProtectionVetos = serverProtectionVetosStore.values().toArray(
          new ServerProtectionVeto[serverProtectionVetosStore.size()]);
    }
  }

  public void unbindServerProtectionVeto(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionVetosStore.remove(serviceReference);
      bundleContext.ungetService(serviceReference);
      serverProtectionVetos = serverProtectionVetosStore.values().toArray(
          new ServerProtectionVeto[serverProtectionVetosStore.size()]);
    }
  }

}

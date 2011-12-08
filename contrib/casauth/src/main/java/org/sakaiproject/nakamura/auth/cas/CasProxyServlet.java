package org.sakaiproject.nakamura.auth.cas;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(paths = { "/system/sling/cas/proxy" }, methods = { "GET", "POST" })
public class CasProxyServlet extends SlingSafeMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(CasProxyServlet.class);

  private static final long serialVersionUID = 1L;

  @Reference
  protected CasAuthenticationHandler cah;

  @Override
  protected void service(SlingHttpServletRequest request,
      SlingHttpServletResponse response) throws ServletException, IOException {
    LOGGER.debug("CasProxyServlet called");
    String pgtIou = request.getParameter("pgtIou");
    if (pgtIou != null) {
      // This is CAS calling us back with an IOU
      String pgt = request.getParameter("pgtId");
      String userid = cah.getUseridFromIOU(pgtIou);
      if (userid != null) {
        // We've already got an IOU for this user so just save pgtId to ~user/private/cas
        cah.savePgt(userid, pgt, pgtIou);
      } else {
        // No IOU for this user, cache the IOU and ticket
        LOGGER.debug("Caching '{}' as the IOU for '{}'", pgtIou, pgt);
        cah.setpgt(pgtIou, pgt);
      }
    } else {
      // This is the UI asking for a proxy ticket
      String target = request.getParameter("t");
      if (target != null) {
        Session session = StorageClientUtils.adaptToSession(request.getResourceResolver()
            .adaptTo(javax.jcr.Session.class));
        try {
          ContentManager contentManager = session.getContentManager();
          String pgt = (String) contentManager.get(
              LitePersonalUtils.getPrivatePath(request.getRemoteUser()) + "/cas")
              .getProperty(
              "ticket");
          String ticket = cah.getProxyTicket(pgt, target);

          // write ticket to response in json
          response.setContentType("application/json");
          JSONWriter w = new JSONWriter(response.getWriter());
          w.object();
          w.key("proxyticket");
          w.value(ticket);
          w.endObject();
        } catch (StorageClientException e) {
          LOGGER.error("Couldn't retreive proxy granting ticket: ", e);
        } catch (AccessDeniedException e) {
          LOGGER.error("Permission denied trying to retreive proxy granting ticket: ", e);
        } catch (JSONException e) {
          LOGGER.error("Error generating proxy ticket json: ", e);
        }
      } else {
        response.setStatus(HttpServletResponse.SC_OK);
        LOGGER.debug("No parameters found.");
      }
    }
  }
}

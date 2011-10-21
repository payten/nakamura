package org.sakaiproject.nakamura.grouper.webconsole;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.framework.BundleContext;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.sakaiproject.nakamura.grouper.event.BatchOperationsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@Service(value=javax.servlet.Servlet.class)
@Component
public class WebConsolePlugin extends AbstractWebConsolePlugin {

	private static final long serialVersionUID = -810270792160656239L;

	private static Logger log = LoggerFactory.getLogger(WebConsolePlugin.class);

	public static final String LABEL = "grouper";
	@SuppressWarnings("unused")
	@Property(value=LABEL)
	private static final String PROP_LABEL = "felix.webconsole.label";

	public static final String TITLE = "Grouper Sync";
	@SuppressWarnings("unused")
	@Property(value=TITLE)
	private static final String PROP_TITLE = "felix.webconsole.title";

	@Reference
	Repository jcrRepository;

	@Reference
	BatchOperationsManager batchManager;

	@Reference
	TemplateService templateService;

	@Activate
	public void activate(BundleContext bundleContext){
		log.error("activated");
		super.activate(bundleContext);
	}

	@Override
	public String getTitle() {
		// TODO internationalize
		return TITLE;
	}

	@Override
	public String getLabel() {
		return LABEL;
	}

	@Override
	protected void renderContent(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		Session jcrSession = null;
		String message = "";
		String errorMessage = "";
		try {
			if (request.getParameter("doProvisionedGroups") != null){
				batchManager.doProvisionedGroups();
				message += " Started syncing provisioned groups <br>";
			}
			if (request.getParameter("doAdhocGroups") != null){
				batchManager.doAdhocGroups();
				message += "Started syncing ad hoc groups <br>";
			}
			if (request.getParameter("doContacts") != null){
				batchManager.doContacts();
				message += "Started syncing contacts groups <br>";
			}
			if (request.getParameter("groupId") != null){
				batchManager.doOneGroup(request.getParameter("groupId"));
				message += "Started syncing " + request.getParameter("groupId") + "<br>";
			}
		}
		catch (Exception e) {
			errorMessage = e.getMessage();
			log.error("Error while starting batch sync", e);
		}

		try {
			jcrSession = jcrRepository.login();
			Node node = jcrSession.getNode("/var/grouper/webconsole/plugin.html");
			InputStream content = node.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream();

			Map<String,Object> context = new HashMap<String,Object>();
			context.put("request", request);
			context.put("message", message);
			context.put("errorMessage", errorMessage);
			response.getWriter().write(templateService.evaluateTemplate(context, IOUtils.toString(content)));
			jcrSession.logout();

		} catch (LoginException e) {
			log.error("Error logging in.", e);
		} catch (NoSuchWorkspaceException e) {
			log.error("No such workspace.", e);
		} catch (RepositoryException e) {
			log.error("Repository exception.", e);
		}
		finally {
			if (jcrSession != null){
				jcrSession = null;
			}
		}
	}
}

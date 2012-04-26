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
package org.sakaiproject.nakamura.jcr.webconsole;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.webconsole.DefaultVariableResolver;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.osgi.framework.BundleContext;
import org.sakaiproject.nakamura.api.jcr.ContentReloaderService;

/**
 *
 */
@Component
@Service
@Properties({
  @Property(name = WebConsoleConstants.PLUGIN_LABEL, value = "contentreloader")
})
public class ContentReloaderWebConsolePlugin extends SimpleWebConsolePlugin {
  private static final long serialVersionUID = 1L;
  private static final String PARAM_BUNDLE_NAMES = "bundle-name";

  @Reference
  private ContentReloaderService contentReloader;

  private final String TEMPLATE;

  public ContentReloaderWebConsolePlugin() {
    super("contentreloader", "%plugin_title", null);

    TEMPLATE = readTemplateFile("/templates/contentreloader.html");
  }

  @Override
  @Activate @Modified
  public void activate(BundleContext bundleContext) {
    super.activate(bundleContext);
  }

  @Override
  @Deactivate
  public void deactivate() {
    super.deactivate();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String[] bundleNames = req.getParameterValues(PARAM_BUNDLE_NAMES);
    List<String> bundlesReloaded = contentReloader.reloadContent(bundleNames);
    Collections.sort(bundlesReloaded, String.CASE_INSENSITIVE_ORDER);

    DefaultVariableResolver vars = ((DefaultVariableResolver) WebConsoleUtil
        .getVariableResolver(req));

    String status = "Bundles reloaded!<ul><li>" + StringUtils.join(bundlesReloaded, "</li><li>") + "</li></ul>";
    req.getSession().setAttribute("status", status);

    resp.sendRedirect("/system/console/" + getLabel());
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void renderContent(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    DefaultVariableResolver vars = ((DefaultVariableResolver) WebConsoleUtil
        .getVariableResolver(req));

    StringWriter sw = new StringWriter();

    try {
      String[] bundleNames = contentReloader.listLoadedBundles();

      JSONWriter jw = new JSONWriter(sw);
      jw.array();
      for (String bundleName : bundleNames) {
        jw.value(bundleName);
      }
      jw.endArray();
    } catch (RepositoryException e) {
      res.sendError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    } catch (JSONException e) {
      res.sendError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    }

    vars.put("__bundles__", sw.toString());

    Object status = req.getSession().getAttribute("status");
    if (status == null) {
      status = "";
    }
    req.getSession().removeAttribute("status");
    vars.put("status", status);

    res.getWriter().write(TEMPLATE);
  }
}

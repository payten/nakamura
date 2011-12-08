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
package org.sakaiproject.nakamura.webconsole.solr;

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
import org.osgi.framework.BundleContext;
import org.sakaiproject.nakamura.api.search.solr.QueryOutputService;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
@Component
@Service
@Properties({
  @Property(name = WebConsoleConstants.PLUGIN_LABEL, value = "solr")
})
public class SolrWebConsolePlugin extends SimpleWebConsolePlugin {
  private static final long serialVersionUID = 1L;

  @Reference
  private QueryOutputService queryOutput;

  private final String TEMPLATE;

  public SolrWebConsolePlugin() {
    super("solr", "%plugin_title", new String[] { "/dev/css/sakai/main.css" });

    TEMPLATE = readTemplateFile("/templates/solr.html");
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
    super.doGet(req, resp);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void renderContent(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    DefaultVariableResolver vars = (DefaultVariableResolver) WebConsoleUtil
        .getVariableResolver(req);
    vars.put("total_solr_docs", queryOutput.getSolrDocCount());
    vars.putAll(queryOutput.collectOptions(req, null));
    vars.putAll(queryOutput.collectForm(req, null));

    PrintWriter writer = res.getWriter();
    writer.write(TEMPLATE);
    try {
      writer.write("<pre id='output'>");
      queryOutput.writeBody(req, writer);
      writer.write("</pre>");
    } catch (RepositoryException e) {
      throw new ServletException(e.getMessage(), e);
    }
  }
}

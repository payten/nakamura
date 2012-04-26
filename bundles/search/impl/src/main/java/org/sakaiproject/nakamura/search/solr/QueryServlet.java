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
package org.sakaiproject.nakamura.search.solr;

import com.google.common.collect.Maps;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.search.solr.QueryOutputService;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.sakaiproject.nakamura.util.NodeInputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

@ServiceDocumentation(
  name = "Solr Debug Servlet",
  description = "Provide a web interface to perform arbitrary queries against Solr.",
  bindings = {
    @ServiceBinding(
      type = BindingType.PATH,
      bindings = { "/system/query" }
    )
  },
  okForVersion = "1.2",
  methods = {
    @ServiceMethod(
      name = "GET",
      description = {"Send a request to Solr.<br>" +
                     "Any parameters provided will be passed through to Solr by calling setParam() on a SolrQuery object. " +
                     "Example queries:<br>" +
                     "<pre>http://localhost:8080/system/query?q=fish&sort=score asc&rows=10&indent=1\n" +
                     "http://localhost:8080/system/query?qt=/admin/luke&fl=email&numTerms=100&indent=1\n" +
                     "</pre>" +
                     "Browse the Solr Wiki at http://wiki.apache.org/solr/ for examples of query types and their corresponding parameters."
                     },
      response = {
        @ServiceResponse(code = 200, description = "The result of returning toString() on the object returned by Solr.")
      }
    )
  }
)
@Component(enabled = false)
@SlingServlet(methods = "GET", paths = "/system/query", generateComponent = false)
@Properties({
  @Property(name = Constants.SERVICE_VENDOR, value = "The Sakai Foundation"),
  @Property(name = Constants.SERVICE_DESCRIPTION, value = "Perform arbitrary queries against Solr.  WARNING: do not enable in production without separately protecting the /system/query URL.")
})
public class QueryServlet extends SlingAllMethodsServlet {
  static final long serialVersionUID = -7250872090976232073L;

  public static final String QUERY_TEMPLATE = "sakai.query.template";
  static final String DEFAULT_QUERY_TEMPLATE = "templates/query.html";
  @Property(name = QUERY_TEMPLATE, value = DEFAULT_QUERY_TEMPLATE)
  private String queryTemplate;

  @Reference
  private QueryOutputService queryOutput;

  @Reference
  private SlingRepository slingRepo;

  @Reference
  private TemplateService tmplService;

  private static final String RESOURCE_TMPL = "%s/resources/%s.properties";

  @Activate @Modified
  protected void activate(Map<?, ?> props) {
    queryTemplate = PropertiesUtil.toString(props.get(QUERY_TEMPLATE), DEFAULT_QUERY_TEMPLATE);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  /**
   * Generic request handler. The only difference between GET and POST requests are that
   * the output is help for GET, query results for POST.
   *
   * @param req
   * @param res
   * @throws ServletException
   * @throws IOException
   */
  public void handleRequest(SlingHttpServletRequest req, SlingHttpServletResponse res)
      throws ServletException, IOException {
    javax.jcr.Session jcrSession = null;
    try {
      jcrSession = slingRepo.login();

      // read in the template to use for the main page
      String resPathBase = req.getResource().getPath();
      String tmplPath = resPathBase + "/" + queryTemplate;
      Node templateNode = jcrSession.getNode(tmplPath);
      NodeInputStream tmplNis = JcrUtils.getInputStreamForNode(templateNode);

      // produce the body to fill in the template
      StringWriter bodyWriter = new StringWriter();
      queryOutput.writeBody(req, bodyWriter);

      // collect variables needed to fill out the template
      Map<String, Object> props = Maps.newHashMap();

      long solrDocCount = queryOutput.getSolrDocCount();
      props.put("total_solr_docs", solrDocCount);
      props.put("output", bodyWriter.toString());

      // load previous parameters
      queryOutput.collectForm(req, props);

      // load the resource bundle for i18n
      Locale reqLocale = req.getLocale();
      String locale = null != reqLocale ? reqLocale.getLanguage()
          : Locale.getDefault().getLanguage();
      if (!jcrSession.itemExists(String.format(RESOURCE_TMPL, resPathBase, locale))) {
        locale = "en";
      }
      java.util.Properties resProps = new java.util.Properties();
      Node resNode = jcrSession.getNode(String.format(RESOURCE_TMPL, resPathBase, locale));
      resProps.load(JcrUtils.getInputStreamForNode(resNode).getInputStream());

      for (Entry<Object, Object> entry: resProps.entrySet()) {
        props.put((String) entry.getKey(), entry.getValue());
      }

      // put it all together
      String results = tmplService.evaluateTemplate(props,
          new InputStreamReader(tmplNis.getInputStream()));
      res.getWriter().print(results);
    } catch (RepositoryException e) {
      throw new ServletException(e.getMessage(), e);
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
  }
}
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
package org.sakaiproject.nakamura.search.solr;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.QueryOutputService;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.sakaiproject.nakamura.util.NodeInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

@Component
@Service(QueryOutputService.class)
@Properties({
  @Property(name = Constants.SERVICE_VENDOR, value = "The Sakai Foundation"),
  @Property(name = Constants.SERVICE_DESCRIPTION, value = "Perform arbitrary queries against Solr.")
})
public class QueryOutputServiceImpl implements QueryOutputService {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryOutputServiceImpl.class);

  public static final String HELP_FILE = "sakai.query.help.file";
  static final String DEFAULT_HELP_FILE = "/system/query/templates/help.txt";
  @Property(name = HELP_FILE, value = DEFAULT_HELP_FILE)
  private String helpFile;

  @Reference
  private SolrServerService solrServerService;

  @Reference
  private SolrSearchServiceFactory searchFactory;

  @Reference
  private Repository repo;

  @Reference
  private SlingRepository slingRepo;

  private Set<String> IGNORE_PARAMS = ImmutableSet.of("q", "addReaders", "asAnon", "indent");

  private class SolrOutputIndenter {

    final int tabwidth = 4;

    private int indent = 0;


    private void newlineIndent(StringBuilder sb) {
        sb.append("\n");
        for (int i = 0; i < (indent * tabwidth); i++) {
          sb.append(" ");
        }
    }


    public String indent(String s) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);

        if (ch == '{') {
          sb.append(ch);
          indent++;
          newlineIndent(sb);
        } else if (ch == '[') {
          sb.append(ch);
          indent++;
          newlineIndent(sb);
        } else if (ch == ']') {
          indent--;
          newlineIndent(sb);
          sb.append(ch);
        } else if (ch == ',') {
          sb.append(ch);
          newlineIndent(sb);
          if (s.charAt(i + 1) == ' ') {
            // Eat the space following the comma too.
            i++;
          }
        } else if (ch == '}') {
          indent--;
          newlineIndent(sb);
          sb.append(ch);
        } else {
          sb.append(ch);
        }
      }

      return sb.toString();
    }
  }

  @Activate @Modified
  protected void activate(Map<?, ?> props) {
    helpFile = PropertiesUtil.toString(props.get(HELP_FILE), DEFAULT_HELP_FILE);
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.QueryOutputService#writeBody(javax.servlet.http.HttpServletRequest, java.io.Writer)
   */
  public void writeBody(HttpServletRequest request, Writer w)
      throws RepositoryException, ServletException, IOException {
    if ("POST".equals(request.getMethod())) {
      if (!StringUtils.isBlank(request.getParameter("q"))) {
        handleQuery(request, w);
      } else if (!StringUtils.isBlank(request.getParameter("type"))) {
        handleReindexing(request, w);
      }
    } else {
      writeHelp(w);
    }
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.QueryOutputService#getSolrDocCount()
   */
  public long getSolrDocCount() {
    SolrQuery solrQuery = new SolrQuery("*:*");
    solrQuery.setRows(0);
    long numFound = -1;

    try {
      QueryResponse queryResp = solrServerService.getServer().query(solrQuery);
      numFound = queryResp.getResults().getNumFound();
    } catch (SolrServerException e) {
      LOGGER.warn(e.getMessage(), e);
    }
    return numFound;
  }

  /**
   * Write the help information to the response.
   *
   * @param w
   * @throws RepositoryException
   * @throws IOException
   */
  private void writeHelp(Writer w) throws RepositoryException, IOException {
    javax.jcr.Session jcrSession = null;
    try {
      jcrSession = slingRepo.login();
      Node helpNode = jcrSession.getNode(helpFile);
      NodeInputStream content = JcrUtils.getInputStreamForNode(helpNode);
      IOUtils.copy(content.getInputStream(), w);
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
  }

  /**
   * Write a status line to the response.
   *
   * @param w
   * @param text
   * @throws IOException
   */
  private void writeStatus(Writer w, String text) throws IOException {
    w.write("<div class='statline'>");
    w.write(text);
    w.write("</div>");
  }

  /**
   * Handle the query that is being requested.
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  private void handleQuery(HttpServletRequest req, Writer writer)
      throws ServletException, IOException {
    QueryResponse queryResponse = null;

    try {
      if (!StringUtils.isBlank(req.getParameter("asAnon")) || !StringUtils.isBlank(req.getParameter("addReaders"))) {
        queryResponse = queryWithReaders((SlingHttpServletRequest) req);
      } else {
        queryResponse = queryDirect(req);
      }

      String result = queryResponse.getResponse().toString();
      if (req.getParameter("indent") != null) {
        result = new SolrOutputIndenter().indent(result);
      }

      writer.write(result);
    } catch (Exception e) {
      throw new ServletException(e.getMessage(), e);
    }
  }

  /**
   * Directly query solr with the provided query and options. No secondary filtering is added.
   *
   * @param params
   * @return
   * @throws SolrServerException
   */
  private QueryResponse queryDirect(HttpServletRequest req)
      throws SolrServerException {
    QueryResponse queryResponse;
    SolrServer server = solrServerService.getServer();

    SolrQuery q = new SolrQuery(req.getParameter("q"));

    collectOptions(req, q);

    queryResponse = server.query(q);
    return queryResponse;
  }

  /**
   * Query solr by adding extra filtering based on the logged in user and what they can
   * see in the index.
   *
   * @param request
   * @param params
   * @return
   * @throws SolrSearchException
   */
  private QueryResponse queryWithReaders(SlingHttpServletRequest req) throws SolrSearchException {
    QueryResponse queryResponse;
    boolean asAnon = !StringUtils.isBlank(req.getParameter("asAnon"));
    Query query = new Query(req.getParameter("q"));

    query.getOptions().putAll(collectOptions(req, null));

    SolrSearchResultSetImpl rs = (SolrSearchResultSetImpl) searchFactory
        .getSearchResultSet(req, query, asAnon);
    queryResponse = rs.getQueryResponse();
    return queryResponse;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.QueryOutputService#collectOptions(javax.servlet.http.HttpServletRequest, org.apache.solr.client.solrj.SolrQuery)
   */
  public Map<String, Object> collectOptions(HttpServletRequest req, SolrQuery solrQuery) {
    Map<String, Object> opts = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    Map<String, String[]> paramMap = req.getParameterMap();
    for (Entry<String, String[]> param : paramMap.entrySet()) {
      String paramName = param.getKey();
      String[] paramVals = param.getValue();
      if (!IGNORE_PARAMS.contains(paramName)) {
        String[] saveVal = null;
        if (paramVals.length == 1) {
          if (!StringUtils.isBlank(paramVals[0])) {
            saveVal = paramVals;
          }
        } else {
          saveVal = paramVals;
        }
        // add the option if the value was acceptible
        if (saveVal != null) {
          opts.put(paramName, saveVal);
          if (solrQuery != null) {
            solrQuery.add(paramName, saveVal);
          }
        }
      }
    }

    return opts;
  }

  public Map<String, Object> collectForm(HttpServletRequest req, Map<String, Object> props) {
    if (props == null) {
      props = Maps.newHashMap();
    }
    // load previous parameters
    props.put("q", StringUtils.defaultString(req.getParameter("q")));
    props.put("fq", StringUtils.defaultString(req.getParameter("fq")));
    props.put("fl", StringUtils.defaultString(req.getParameter("fl")));
    props.put("sort", StringUtils.defaultString(req.getParameter("sort")));
    props.put("start", StringUtils.defaultString(req.getParameter("start")));
    props.put("rows", StringUtils.defaultString(req.getParameter("rows")));
    props.put("addReaders", checkedBox(req, "addReaders"));
    props.put("asAnon", checkedBox(req, "asAnon"));
    props.put("indent", checkedBox(req, "indent"));
    return props;
  }

  private String checkedBox(HttpServletRequest req, String name) {
    String retval = "";
    if (req.getParameter(name) != null) {
      retval = "checked='checked'";
    }
    return retval;
  }

  /**
   * Handle a reindexing request.
   *
   * @param req
   * @param w
   * @throws ServletException
   * @throws IOException
   */
  private void handleReindexing(HttpServletRequest req, Writer w)
      throws ServletException, IOException {
    String type = req.getParameter("type");

    try {
      if ("auth".equalsIgnoreCase(type)) {
        indexAuthorizables(w);
      } else if ("content".equalsIgnoreCase(type)) {
        indexContent(w);
      } else if ("all".equalsIgnoreCase(type)) {
        indexAuthorizables(w);
        indexContent(w);
      } else {
        throw new IllegalArgumentException("Unable to handle request reindex type [" + type + "]");
      }
    } catch (AccessDeniedException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    }
  }

  /**
   * Triggers indexing of all authorizables. Returns immediately without waiting for
   * indexing.
   *
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private void indexAuthorizables(Writer w)
      throws AccessDeniedException, StorageClientException, IOException {
    Session session = null;
    try {
      session = repo.loginAdministrative();
      AuthorizableManager authMgr = session.getAuthorizableManager();
      authMgr.triggerRefreshAll();
      writeStatus(w, "Reindexing of all authorizables triggered. Please watch the logs for progress.");
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }

  /**
   * Triggers indexing of all content. Returns immediately without waiting for indexing.
   *
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private void indexContent(Writer w) throws AccessDeniedException,
      StorageClientException, IOException {
    Session session = null;
    try {
      session = repo.loginAdministrative();
      ContentManager contentMgr = session.getContentManager();
      contentMgr.triggerRefreshAll();
      writeStatus(w, "Reindexing of all content triggered. Please watch the logs for progress.");
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }
}

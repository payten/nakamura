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
package org.sakaiproject.nakamura.api.search.solr;

import org.apache.solr.client.solrj.SolrQuery;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public interface QueryOutputService {

  /**
   * Common entry point for writing out the body of the form as well as handle querying,
   * re-indexing or showing help.
   *
   * @param request
   * @param w
   * @param formAction
   * @param showReindex
   * @param showReaders
   * @throws ServletException
   * @throws IOException
   */
  void writeBody(HttpServletRequest request, Writer w) throws RepositoryException,
      ServletException, IOException;

  /**
   * Get the count of total solr documents in the index.
   *
   * @return
   */
  long getSolrDocCount();

  /**
   * Collect options from the request into a map skipping over any
   *
   * @param req
   * @param solrQuery
   * @return
   */
  Map<String, Object> collectOptions(HttpServletRequest req, SolrQuery solrQuery);

  /**
   * Collect values sent in from the form to be repopulated in the response.
   *
   * @param req
   *          The request to get the props from.
   * @param props
   *          A map to add props to. If null, a new map is created.
   * @return The modified Map of props.
   */
  Map<String, Object> collectForm(HttpServletRequest req, Map<String, Object> props);
}
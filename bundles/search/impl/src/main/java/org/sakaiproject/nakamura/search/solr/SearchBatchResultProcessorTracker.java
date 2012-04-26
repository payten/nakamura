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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;

import java.util.Map;

@Component(immediate = true, metatype = true)
@Properties(value = {@Property(name = "service.vendor", value = "The Sakai Foundation")})
@Service(value = SearchBatchResultProcessorTracker.class)
@References(value = {
        @Reference(name = "SearchBatchResultProcessor", referenceInterface = SolrSearchBatchResultProcessor.class,
                bind = "bindHelper", unbind = "unbindHelper",
                cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)})
public class SearchBatchResultProcessorTracker extends AbstractSolrSearchServletHelperTracker<SolrSearchBatchResultProcessor>
        implements SolrSearchServletHelperTracker<SolrSearchBatchResultProcessor> {

  public SearchBatchResultProcessorTracker() {
    super(SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, SolrSearchBatchResultProcessor.DEFAULT_BATCH_PROCESSOR_PROP);
  }

  protected void bindHelper(SolrSearchBatchResultProcessor helper, Map<?, ?> props) {
    bind(helper, props);
  }

  protected void unbindHelper(SolrSearchBatchResultProcessor helper, Map<?, ?> props) {
    unbind(helper, props);
  }

}

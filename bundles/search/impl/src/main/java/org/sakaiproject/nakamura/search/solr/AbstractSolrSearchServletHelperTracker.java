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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSolrSearchServletHelperTracker<T> {

  private Map<String, T> helpersByName = new ConcurrentHashMap<String, T>();

  private T defaultHelper = null;

  private final String helperNameOsgiProp;

  private final String defaultOsgiProp;

  protected AbstractSolrSearchServletHelperTracker(String helperNameOsgiProp, String defaultOsgiProp) {
    this.helperNameOsgiProp = helperNameOsgiProp;
    this.defaultOsgiProp = defaultOsgiProp;
  }

  public T getByName(String name) {
    T helper = helpersByName.get(name);
    if (helper == null) {
      helper = this.defaultHelper;
    }
    return helper;
  }

  protected void bind(T helper, Map<?, ?> props) {
    addHelper(helper, props);
  }

  protected void unbind(T helper, Map<?, ?> props) {
    removeHelper(helper, props);
  }

  protected void addHelper(T helper, Map<?, ?> props) {
    String[] helperNames = getSetting(props.get(this.helperNameOsgiProp), new String[0]);
    for (String name : helperNames) {
      this.helpersByName.put(name, helper);
    }

    // bit of a kludge until I can figure out why felix doesn't wire up the default
    // processor even though it finds a matching service.
    boolean defaultProcessor = getSetting(props.get(this.defaultOsgiProp), false);
    if (defaultProcessor) {
      this.defaultHelper = helper;
    }
  }

  protected void removeHelper(T helper, Map<?, ?> props) {
    String[] helperNames = getSetting(props.get(this.helperNameOsgiProp), new String[0]);
    for (String name : helperNames) {
      this.helpersByName.remove(name);
    }

    // bit of a kludge until I can figure out why felix doesn't wire up the default
    // processor even though it finds a matching service.
    boolean defaultProcessor = getSetting(props.get(this.defaultOsgiProp), false);
    if (defaultProcessor) {
      this.defaultHelper = null;
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T getSetting(Object o, T defaultValue) {
    if (o == null) {
      return defaultValue;
    }
    return (T) o;
  }

  private String[] getSetting(Object o, String[] defaultValue) {
    if (o == null) {
      return defaultValue;
    }
    if (o.getClass().isArray()) {
      return (String[]) o;
    }
    return new String[]{(String) o};
  }

}

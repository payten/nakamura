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
package org.sakaiproject.nakamura.util.telemetry;

import com.google.common.collect.Maps;

import java.util.concurrent.ConcurrentMap;

class TelemetryEntry {
  private String module;
  private String service;
  private long lastUpdate;
  private TelemetryCounter.Status status;
  private ConcurrentMap<String, TelemetryData> metrics;

  public TelemetryEntry(String module, String service, TelemetryCounter.Status status) {
    this.module = module;
    this.service = service;
    this.status = status;
    lastUpdate = System.currentTimeMillis() / 1000;
    metrics = Maps.newConcurrentMap();
  }

  public void addMetric(String name, TelemetryData d) {
    lastUpdate = System.currentTimeMillis() / 1000;
    metrics.putIfAbsent(name, d);
    TelemetryData oldVal;
    do {
      oldVal = metrics.get(name);
    } while (!metrics.replace(name, oldVal, d));
  }

  public TelemetryData getMetric(String metricName) {
    return metrics.get(metricName);
  }

  public String getModule() {
    return module;
  }

  public String getService() {
    return service;
  }

  public long getLastUpdate() {
    return lastUpdate;
  }

  public TelemetryCounter.Status getStatus() {
    return status;
  }

  public ConcurrentMap<String, TelemetryData> getMetrics() {
    return metrics;
  }
}

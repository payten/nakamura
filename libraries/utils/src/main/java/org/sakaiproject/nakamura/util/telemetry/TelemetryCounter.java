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

public class TelemetryCounter {
  protected static ConcurrentMap<String, TelemetryEntry> counters = Maps.newConcurrentMap();

  public static void clear() {
    counters = Maps.newConcurrentMap();
  }

  public static void incrementValue(String moduleName, String serviceName, String metricName) {
    String compositeKey = moduleName + "::" + serviceName;
    TelemetryEntry entry = counters.get(compositeKey);
    Long value;
    if (entry == null) {
      counters.put(compositeKey, new TelemetryEntry(moduleName, serviceName, Status.OK));
      entry = counters.get(compositeKey);
    }
    TelemetryData telemetryData = entry.getMetric(metricName);
    if (telemetryData == null) {
      value = Long.valueOf(1);
    } else {
      if (!"L".equalsIgnoreCase(telemetryData.type)) {
        throw new IllegalArgumentException("Attempting to increment a metric that isn't Long type.");
      }
      value = Long.parseLong(telemetryData.value) + 1;
    }
    entry.addMetric(metricName, new TelemetryData(value));
  }

  public static enum Status {
    BAD, WARNING, OK
  }
}

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
package org.sakaiproject.nakamura.messagebucket;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucket;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucketException;
import org.sakaiproject.nakamura.api.messagebucket.Waiter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MessageBucketImpl implements MessageBucket {

  private Map<Waiter, Waiter> waiters = Maps.newConcurrentMap();
  private Map<String, HttpServletRequest> bound = Maps.newConcurrentMap();
  private boolean ready = false;
  private Map<String, Object> messagePayload;

  public boolean isReady() {
    return ready;
  }

  public void addWaiter(Waiter waiter) {
    waiters.put(waiter, waiter);
  }

  public void removeWaiter(Waiter waiter) {
    waiters.remove(waiter);
  }

  public void send(HttpServletResponse response) throws MessageBucketException {
    try {
      if (ready) {
        ExtendedJSONWriter e = new ExtendedJSONWriter(response.getWriter());
        e.valueMap(messagePayload);
      } else {
        throw new MessageBucketException("Message not ready");
      }
    } catch (IOException e) {
      throw new MessageBucketException(e.getMessage(), e);
    } catch (JSONException e) {
      throw new MessageBucketException(e.getMessage(), e);
    }
  }

  public void unbind(String token, HttpServletRequest request) {
    bound.put(token, request);
  }

  public void bind(String token, HttpServletRequest request) {
    bound.remove(token);
  }

  public void markReady(Map<String, Object> messagePayload) {
    this.messagePayload = messagePayload;
    ready = true;
    for (Waiter w : ImmutableSet.copyOf(waiters.keySet())) {
      w.resume();
    }
  }

}

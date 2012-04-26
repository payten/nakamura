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
package org.sakaiproject.nakamura.http.qos;

import org.mortbay.util.ajax.Continuation;

import java.util.Queue;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletRequest;

public class QoSControl {

  private Semaphore semaphore;
  private int priority;
  private Queue<Continuation>[] priorityQueue;
  private long timeout;

  public QoSControl(Queue<Continuation>[] priorityQueue, int nRequests, int priority,
      long timeout) {
    semaphore = new Semaphore(nRequests, true);
    this.priority = priority;
    this.priorityQueue = priorityQueue.clone();
    this.timeout = timeout;
  }

  public Semaphore getSemaphore() {
    return semaphore;
  }

  public int getPriority(ServletRequest request) {
    return priority;
  }

  public Queue<Continuation>[] getPriorityQueue() {
    return priorityQueue;
  }

  public long getTimeout() {
    return timeout;
  }

}

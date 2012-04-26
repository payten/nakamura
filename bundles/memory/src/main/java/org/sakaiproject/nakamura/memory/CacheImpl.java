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
package org.sakaiproject.nakamura.memory;

import com.google.common.collect.Sets;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;

import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class CacheImpl<V> implements Cache<V> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheImpl.class);
  private String cacheName;
  private net.sf.ehcache.Cache cache;
  private CacheScope scope;
  private boolean checkPayloadClasses;
  private Set<String> loadedClasses = Sets.newHashSet();

  /**
   * @param cacheManager
   * @param name
   * @param scope
   */
  public CacheImpl(CacheManager cacheManager, String name, CacheScope scope) {
    if (name == null) {
      cacheName = "default";
    } else {
      cacheName = name;
    }
    this.scope = scope;
    synchronized (cacheManager) {
      cache = cacheManager.getCache(cacheName);
      if (cache == null) {
        cacheManager.addCache(cacheName);
        cache = cacheManager.getCache(cacheName);
        if (cache == null) {
          throw new RuntimeException("Failed to create Cache with name " + cacheName);
        }
      }
    }
    checkPayloadClasses = false;
    CacheConfiguration cacheConfiguration = cache.getCacheConfiguration();
    if (CacheScope.CLUSTERREPLICATED.equals(scope) || cacheConfiguration.isDiskPersistent() || cacheConfiguration.isEternal() || cacheConfiguration.isOverflowToDisk()) {
      checkPayloadClasses = true;
    }
    // this isn't really checking to see if the cache is configured to replicate payloads, but there doesn't appear to be
    // a way of finding that out from the Cache Configuration object.
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#clear()
   */
  public void clear() {
    cache.removeAll();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#containsKey(java.lang.String)
   */
  public boolean containsKey(String key) {
    return cache.isKeyInCache(key);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#get(java.lang.String)
   */
  public V get(String key) {
    Element e = cache.get(key);
    if (e == null) {
      return null;
    }
    return stats(e.getObjectValue());
  }

  @SuppressWarnings("unchecked")
  private V stats(Object objectValue) {
    if (objectValue == null) {
      TelemetryCounter.incrementValue("memory", "Cache", "misses");
    } else {
      TelemetryCounter.incrementValue("memory", "Cache", "hits");
    }
    return (V) objectValue;
  }

  /**
   * {@inherit-doc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#put(java.lang.String, java.lang.Object)
   */
  @SuppressWarnings("unchecked")
  public V put(String key, V payload) {
    V previous = null;
    if (cache.isKeyInCache(key)) {
      Element e = cache.get(key);
      if (e != null) {
        previous = (V) e.getObjectValue();
      }
    }
    if (checkPayloadClasses
        && !loadedClasses.contains(payload.getClass().getName())) {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      try {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(payload);
        oos.flush();
        ByteArrayInputStream bin = new ByteArrayInputStream(
            baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bin);
        Object o = ois.readObject();
        if (!o.getClass().equals(payload.getClass())) {
          throw new IllegalArgumentException(
              "Class "
                  + payload.getClass()
                  + " may not be added to cache "
                  + cacheName
                  + "  as it would result in a ClassCast exception, please ensure the class is exported ");
        }
        loadedClasses.add(payload.getClass().getName());
      } catch (IOException e) {
        LOGGER.error("Unable to check serialization " + e.getMessage(),
            e);
      } catch (ClassNotFoundException e) {
        LOGGER.error(e.getMessage(), e);
        throw new IllegalArgumentException("Class "
            + payload.getClass() + " may not be added to cache "
            + cacheName + " serialization error cause:"
            + e.getMessage());
      } finally {
        Thread.currentThread().setContextClassLoader(cl);
      }
    }
    cache.put(new Element(key, payload));
    return previous;
  }

  /**
   * {@inherit-doc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#remove(java.lang.String)
   */
  public void remove(String key) {
    cache.remove(key);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#removeChildren(java.lang.String)
   */
  public void removeChildren(String key) {
    cache.remove(key);
    if (!key.endsWith("/")) {
      key = key + "/";
    }
    List<?> keys = cache.getKeys();
    for (Object k : keys) {
      if (((String) k).startsWith(key)) {
        cache.remove(k);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.Cache#list()
   */
  @SuppressWarnings("unchecked")
  public List<V> list() {
    List<String> keys = cache.getKeys();
    List<V> values = new ArrayList<V>();
    for (String k : keys) {
      Element e = cache.get(k);
      if (e != null) {
        values.add((V) e.getObjectValue());
      }
    }
    return values;
  }

  public void checkCompatableScope(CacheScope scope) {
    if (!scope.equals(this.scope)) {
      throw new IllegalStateException("The cache called " + cacheName
          + " is a " + this.scope
          + " cache and cant be re-used as a " + scope + " cache");
    }
  }

}

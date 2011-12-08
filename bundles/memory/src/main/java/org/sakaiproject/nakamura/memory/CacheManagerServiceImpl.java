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

import com.google.common.collect.Maps;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.management.ManagementService;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.sling.commons.osgi.PropertiesUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;

/**
 * The <code>CacheManagerServiceImpl</code>
 */
@Component(immediate = true, metatype=true)
@Service(value=CacheManagerService.class)
public class CacheManagerServiceImpl implements CacheManagerService {

  public static final String DEFAULT_CACHE_CONFIG = "sling/ehcacheConfig.xml";
  public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
  public static final String DEFAULT_CACHE_STORE = "sling/ehcacheStore";

  @Property( value = DEFAULT_CACHE_CONFIG)
  public static final String CACHE_CONFIG = "cache-config";

  @Property( value = DEFAULT_BIND_ADDRESS)
  public static final String BIND_ADDRESS = "bind-address";

  @Property( value = DEFAULT_CACHE_STORE)
  public static final String CACHE_STORE = "cache-store";

  @Property(value = "The Sakai Foundation")
  static final String SERVICE_VENDOR = "service.vendor";

  @Property(value = "Cache Manager Service Implementation")
  static final String SERVICE_DESCRIPTION = "service.description";

  private static final String CONFIG_PATH = "res://org/sakaiproject/nakamura/memory/ehcacheConfig.xml";
  private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerServiceImpl.class);
  private CacheManager cacheManager;
  private Map<String, Cache<?>> caches = new HashMap<String, Cache<?>>();
  private ThreadLocalCacheMap requestCacheMapHolder = new ThreadLocalCacheMap();
  private ThreadLocalCacheMap threadCacheMapHolder = new ThreadLocalCacheMap();

  public CacheManagerServiceImpl() throws IOException {
    create();
  }

  private void create() throws IOException {
    LOGGER.info("Loading Resource using "+this.getClass().getClassLoader());
    LOGGER.info("Locally Stream was "+this.getClass().getClassLoader().getResourceAsStream(CONFIG_PATH));
    InputStream in = ResourceLoader.openResource(CONFIG_PATH, this.getClass().getClassLoader());
    cacheManager = new CacheManager(in);
    in.close();

    /*
     * Add in a shutdown hook, for safety
     */
    Runtime.getRuntime().addShutdownHook(new Thread() {
      /*
       * (non-Javadoc)
       *
       * @see java.lang.Thread#run()
       */
      @Override
      public void run() {
        try {
          CacheManagerServiceImpl.this.stop();
        } catch (Throwable t) {

          // I really do want to swallow this, and make the shutdown clean for
          // others
        }
      }
    });

    // register the cache manager with JMX
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    ManagementService.registerMBeans(cacheManager, mBeanServer, true, true,
        true, true);

  }

   @Activate
   protected void activate(Map<String, Object> properties) throws FileNotFoundException, IOException {
	  String config = PropertiesUtil.toString(properties.get(CACHE_CONFIG), DEFAULT_CACHE_CONFIG);
	  File configFile = new File(config);
	  ClassLoader cl = Thread.currentThread().getContextClassLoader();
	  try {
		  Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
		  LOGGER.info("Context Classloader was {} now {} ",cl,this.getClass().getClassLoader());
		  if ( configFile.exists() ) {
			  LOGGER.info("Configuring Cache from {} ",configFile.getAbsolutePath());
			  InputStream in = null;
			  try {
				  in = processConfig(new FileInputStream(configFile), properties);
				  cacheManager = new CacheManager(in);
			  } finally {
				  if ( in != null ) {
					  in.close();
				  }
			  }
		  } else {
			    LOGGER.info("Configuring Cache from Classpath Default {} ", CONFIG_PATH);
			    InputStream in = processConfig(ResourceLoader.openResource(CONFIG_PATH, this.getClass().getClassLoader()), properties);
			    if ( in == null ) {
				throw new IOException("Unable to open config at classpath location "+CONFIG_PATH);
			    }
			    cacheManager = new CacheManager(in);
			    in.close();
		  }
	  } finally {
		  Thread.currentThread().setContextClassLoader(cl);
		  LOGGER.info("Context Classloader reset was {} now {} ",this.getClass().getClassLoader(),cl);
	  }
   }

  protected InputStream processConfig(InputStream configFile, Map<String,Object> properties) {
    StringBuilder config = new StringBuilder();
    Pattern p = Pattern.compile("\\$\\{([\\S]+)}");
    Scanner scanner = new Scanner(configFile);
    while(scanner.hasNextLine()) {
      String configLine = scanner.nextLine();
      Matcher m = p.matcher(configLine);
      while (m.find()) {
        String propKey = m.group(1);
        configLine = StringUtils.replace(configLine, m.group(), PropertiesUtil.toString(properties.get(propKey), ""));
      }
      config.append(configLine + "\n");
    }
    return IOUtils.toInputStream(config);
  }

  public static void main(String[] args) throws Exception {

  }

  /**
   * perform a shutdown
   */
  public void stop() {
    cacheManager.shutdown();
    // we really want to notify all threads that have maps
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.CacheManagerService#getCache(java.lang.String, )
   */
  public <V> Cache<V> getCache(String name, CacheScope scope) {
    switch (scope) {
    case INSTANCE:
      return getInstanceCache(name, scope);
    case CLUSTERINVALIDATED:
      return getInstanceCache(name, scope);
    case CLUSTERREPLICATED:
      return getInstanceCache(name, scope);
    case REQUEST:
      return getRequestCache(name);
    case THREAD:
      return getThreadCache(name);
    default:
      return getInstanceCache(name, scope);
    }
  }

  /**
   * Generate a cache bound to the thread.
   *
   * @param name
   * @return
   */
  @SuppressWarnings("unchecked")
  private <V> Cache<V> getThreadCache(String name) {
    Map<String, Cache<?>> threadCacheMap = threadCacheMapHolder.get();
    Cache<V> threadCache = (Cache<V>) threadCacheMap.get(name);
    if (threadCache == null) {
      threadCache = new MapCacheImpl<V>(name, CacheScope.THREAD);
      threadCacheMap.put(name, threadCache);
    }
    return threadCache;
  }

  /**
   * Generate a cache bound to the request
   *
   * @param name
   * @return
   */
  @SuppressWarnings("unchecked")
  private <V> Cache<V> getRequestCache(String name) {
    Map<String, Cache<?>> requestCacheMap = requestCacheMapHolder.get();
    Cache<V> requestCache = (Cache<V>) requestCacheMap.get(name);
    if (requestCache == null) {
      requestCache = new MapCacheImpl<V>(name, CacheScope.REQUEST);
      requestCacheMap.put(name, requestCache);
    }
    return requestCache;
  }

  /**
   * @param name
   * @return
   */
  @SuppressWarnings("unchecked")
  private <V> Cache<V> getInstanceCache(String name, CacheScope scope) {
    if (name == null) {
      return new CacheImpl<V>(cacheManager, null, scope);
    } else {
      Cache<V> c = (Cache<V>) caches.get(name);
      if (c == null) {
        c = new CacheImpl<V>(cacheManager, name, scope);
        caches.put(name, c);
      }
      return c;
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.memory.CacheManagerService#unbind(org.sakaiproject.nakamura.api.memory.CacheScope)
   */
  public void unbind(CacheScope scope) {
    switch (scope) {
    case REQUEST:
      unbindRequest();
      break;
    case THREAD:
      unbindThread();
      break;
    }
  }

  /**
   *
   */
  private void unbindThread() {
    Map<String, Cache<?>> threadCache = threadCacheMapHolder.get();
    for (Cache<?> cache : threadCache.values()) {
      cache.clear();
    }
    threadCacheMapHolder.remove();
  }

  /**
   *
   */
  private void unbindRequest() {
    Map<String, Cache<?>> requestCache = requestCacheMapHolder.get();
    for (Cache<?> cache : requestCache.values()) {
      cache.clear();
    }
    requestCacheMapHolder.remove();
  }

}

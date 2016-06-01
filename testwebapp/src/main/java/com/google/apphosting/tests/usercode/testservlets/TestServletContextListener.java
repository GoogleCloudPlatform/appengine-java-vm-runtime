/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.tests.usercode.testservlets;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.BackendServiceFactory;

import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Simple {@link ServletContextListener} for testing the initialization environment
 * by storing values from BackendService in the {@link ServletContext}.
 */
public class TestServletContextListener implements ServletContextListener {
  private static final Logger LOG = Logger.getLogger(TestServletContextListener.class.getName());

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    addBackEndServiceInfoToServletContext("contextInitialized", sce.getServletContext());
  }

  static void addBackEndServiceInfoToServletContext(String tag, ServletContext context) {
    BackendService backends = BackendServiceFactory.getBackendService();
    String currentBackend = backends.getCurrentBackend();
    LOG.fine("Tag=" + tag + " currentBackend=" + currentBackend);
    int currentBackendInstance = backends.getCurrentInstance();
    LOG.fine("Tag=" + tag + " currentBackendInstance=" + currentBackendInstance);
    context.setAttribute(tag + ".currentBackend", currentBackend == null ? "none" : currentBackend);
    context.setAttribute(tag + ".currentBackendInstance", currentBackendInstance);

    String memcache = null;
    try {
      memcache = backends.getBackendAddress("memcache");
    } catch (IllegalStateException ise) {
      memcache = "none";
    } catch (NullPointerException npe) {
      memcache = "nomap";
    }
    LOG.fine("Tag=" + tag + " memcache=" + memcache);
    context.setAttribute(tag + ".memcache", memcache);

    String memcacheInstance0 = null;
    try {
      memcacheInstance0 = backends.getBackendAddress("memcache", 0);
    } catch (IllegalStateException ise) {
      memcacheInstance0 = "none";
    } catch (NullPointerException npe) {
      memcacheInstance0 = "nomap";
    }
    LOG.fine("Tag=" + tag + " memcacheInstance0=" + memcacheInstance0);
    context.setAttribute(tag + ".memcacheInstance0", memcacheInstance0);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    LOG.fine("context destroyed.");
  }
}

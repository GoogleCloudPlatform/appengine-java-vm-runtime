/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.apphosting.vmruntime.jetty9;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

import java.util.logging.Level;

public class AppengineApiConfiguration extends AbstractConfiguration {

  // A class to check if the GAE API is available.
  public static final String GAE_CHECK_CLASS = "com.google.appengine.api.ThreadManager";

  // Hide the all container classes from the webapplication
  // TODO update to use '.' when supported by Jetty
  private static final String[] SERVER_CLASSES = {
    "com.", 
    "javax.", 
    "org.", 
    "mozilla."
  };

  private static final String[] SHARED_CLASSES = {
    // Expose the GAE API classes
    "com.google.appengine.api.LifecycleManager",
    "com.google.apphosting.api.ApiProxy",
    "com.google.apphosting.api.ApiStats",
    "com.google.apphosting.api.CloudTrace",
    "com.google.apphosting.api.CloudTraceContext",
    "com.google.apphosting.api.DeadlineExceededException",
    "com.google.apphosting.runtime.SessionData",
    "com.google.apphosting.runtime.UncatchableError",

    // Expose the standard APIs that are provided by the container
    "javax.servlet.",
    "javax.el.",
    "javax.mail.", // TODO Review
    
    // Expose the standard APIs that are provided by the JVM
    "com.oracle.",
    "com.sun.",
    "javax.accessibility.",
    "javax.activation.",
    "javax.activity.",
    "javax.annotation.",
    "javax.imageio.",
    "javax.jws.",
    "javax.lang.model.",
    "javax.management.",
    "javax.naming.",
    "javax.net.",
    "javax.print.",
    "javax.rmi.",
    "javax.script.",
    "javax.security.",
    "javax.smartcardio.",
    "javax.sound.",
    "javax.sql.",
    "javax.swing.",
    "javax.tools.",
    "javax.transaction.",
    "javax.xml.",
    "jdk.",
    "org.ietf.jgss.",
    "org.jcp.xml.dsig.internal.",
    "org.omg.",
    "org.w3c.dom.",
    "org.xml.",
    "sun.",

    // Expose classes needed for JSP and JSTL
    "org.apache.jasper.runtime.",
    "org.apache.jasper.JasperException",
    "org.apache.el.ExpressionFactoryImpl",
    "org.apache.tomcat.InstanceManager",
    "org.apache.taglibs.",
  };

  @Override
  public void preConfigure(WebAppContext context) {
    for (String systemClass : SERVER_CLASSES) {
      context.addServerClass(systemClass);
    }
    for (String gaeClass : SHARED_CLASSES) {
      // Don't hide shared classes
      context.prependServerClass("-" + gaeClass);
      // Don't allow shared classes to be replaced by webapp
      context.addSystemClass(gaeClass);
    }
  }

  public void configure(WebAppContext context) throws Exception {
    ClassLoader loader = context.getClassLoader();
    try {
      // Test if the appengine api is available
      loader.loadClass(GAE_CHECK_CLASS);
    } catch (Exception ex) {
      VmRuntimeWebAppContext.logger.log(Level.FINE, "No appengined API", ex);
      VmRuntimeWebAppContext.logger.log(
          Level.WARNING, "No appengined API jar including in WEB-INF/lib! Please update your SDK!");

      // The appengine API is not available so we will add it and it's dependencies
      Resource providedApi =
          Resource.newResource(
              URIUtil.addPaths(System.getProperty("jetty.base"), "/lib/gae/provided-api/"));

      if (providedApi != null) {
        String[] list = providedApi.list();
        if (list != null) {
          WebAppClassLoader wloader = (WebAppClassLoader) loader;
          for (String jar : list) {
            wloader.addClassPath(providedApi.addPath(jar));
            VmRuntimeWebAppContext.logger.log(Level.INFO, "Added " + jar + " to webapp classpath");
          }
        }
      }

      // Ensure the API can now be loaded
      loader.loadClass(GAE_CHECK_CLASS);
    }
  }
}

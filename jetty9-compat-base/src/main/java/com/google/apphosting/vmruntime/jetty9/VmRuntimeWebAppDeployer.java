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

package com.google.apphosting.vmruntime.jetty9;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VmRuntimeWebAppDeployer extends AbstractLifeCycle {
  private final ContextHandlerCollection contexts;
  private final String webapp;
  private final Map<String, String> properties;
  private ContextHandler handler;

  public VmRuntimeWebAppDeployer(
      @Name("contexts") ContextHandlerCollection contexts, @Name("webapp") String webapp) {
    this(contexts, webapp, new HashMap<String, String>());
  }

  public VmRuntimeWebAppDeployer(
      @Name("contexts") ContextHandlerCollection contexts,
      @Name("webapp") String webapp,
      @Name("properties") Map<String, String> properties) {
    this.contexts = contexts;
    this.webapp = webapp;
    this.properties = properties;
  }

  @Override
  protected void doStart() throws Exception {

    Resource resource = Resource.newResource(webapp);
    File file = resource.getFile();
    if (!resource.exists()) {
      throw new IllegalStateException("WebApp resouce does not exist " + resource);
    }

    String lcName = file.getName().toLowerCase(Locale.ENGLISH);

    if (lcName.endsWith(".xml")) {
      XmlConfiguration xmlc = new XmlConfiguration(resource.getURI().toURL());
      xmlc.getIdMap().put("Server", contexts.getServer());
      xmlc.getProperties().put("jetty.home", System.getProperty("jetty.home", "."));
      xmlc.getProperties().put("jetty.base", System.getProperty("jetty.base", "."));
      xmlc.getProperties().put("jetty.webapp", file.getCanonicalPath());
      xmlc.getProperties().put("jetty.webapps", file.getParentFile().getCanonicalPath());
      xmlc.getProperties().putAll(properties);
      handler = (ContextHandler) xmlc.configure();
    } else {
      WebAppContext wac = new WebAppContext();
      wac.setWar(webapp);
      wac.setContextPath("/");
    }

    contexts.addHandler(handler);
    if (contexts.isRunning()) {
      handler.start();
    }
  }

  @Override
  protected void doStop() throws Exception {
    if (handler.isRunning()) {
      handler.stop();
    }
    contexts.removeHandler(handler);
    handler = null;
  }

  public Map<String, String> getProperties() {
    return properties;
  }
}

/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.vmruntime.jetty9;

import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.spi.ServiceFactoryFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.runtime.DatastoreSessionStore;
import com.google.apphosting.runtime.DeferredDatastoreSessionStore;
import com.google.apphosting.runtime.MemcacheSessionStore;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.jetty9.SessionManager;
import com.google.apphosting.runtime.timer.Timer;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.http.HttpRequest;
import com.google.apphosting.utils.http.HttpResponse;
import com.google.apphosting.utils.servlet.HttpServletRequestAdapter;
import com.google.apphosting.utils.servlet.HttpServletResponseAdapter;
import com.google.apphosting.vmruntime.CommitDelayingResponseServlet3;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;
import com.google.apphosting.vmruntime.VmEnvironmentFactory;
import com.google.apphosting.vmruntime.VmMetadataCache;
import com.google.apphosting.vmruntime.VmRequestUtils;
import com.google.apphosting.vmruntime.VmRuntimeFileLogHandler;
import com.google.apphosting.vmruntime.VmRuntimeLogHandler;
import com.google.apphosting.vmruntime.VmRuntimeUtils;
import com.google.apphosting.vmruntime.VmTimer;


import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * WebAppContext for VM Runtimes. This class extends the "normal" AppEngineWebAppContext with
 * functionality that installs a request specific thread local environment on each incoming request.
 */
public class VmRuntimeWebAppContext
  extends WebAppContext implements VmRuntimeTrustedAddressChecker {
  private static final Logger logger = Logger.getLogger(VmRuntimeWebAppContext.class.getName());

  // It's undesirable to have the user app override classes provided by us.
  // So we mark them as Jetty system classes, which cannot be overridden.
  private static final String[] SYSTEM_CLASSES = {
    // The trailing dot means these are all Java packages, not individual classes.
    "com.google.appengine.api.",
    "com.google.appengine.tools.",
    "com.google.apphosting.",
    "com.google.cloud.sql.jdbc.",
    "com.google.protos.cloud.sql.",
    "com.google.storage.onestore.",
  };
  // constant.  If it's much larger than this we may need to
  // restructure the code a bit.
  protected static final int MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  private final String serverInfo;

  private final VmMetadataCache metadataCache;
  private final Timer wallclockTimer;
  private VmApiProxyEnvironment defaultEnvironment;
  // Indicates if the context is running via the Cloud SDK, or the real runtime.
  
  boolean isDevMode;
  static {
    // Set SPI classloader priority to prefer the WebAppClassloader.
    System.setProperty(
        ServiceFactoryFactory.USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY, Boolean.TRUE.toString());
    // Use thread context class loader for memcache deserialization.
    System.setProperty(
        MemcacheSerialization.USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY, Boolean.TRUE.toString());
  }

  // List of Jetty configuration only needed if the quickstart process has been
  // executed, so we do not need the webinf, wedxml, fragment and annotation configurations
  // because they have been executed via the SDK.
  private static final String[] quickstartConfigurationClasses  = {
    org.eclipse.jetty.quickstart.QuickStartConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.JettyWebXmlConfiguration.class.getCanonicalName()
  };

  // List of all the standard Jetty configurations that need to be executed when there
  // is no quickstart-web.xml.
  private static final String[] preconfigurationClasses = {
    org.eclipse.jetty.webapp.WebInfConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.WebXmlConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.MetaInfConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.FragmentConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.annotations.AnnotationConfiguration.class.getCanonicalName()
  };

  @Override
  protected void doStart() throws Exception {
    // unpack and Adjust paths.
    Resource base = getBaseResource();
    if (base == null) {
      base = Resource.newResource(getWar());
    }
    Resource dir;
    if (base.isDirectory()) {
      dir = base;
    } else {
      throw new IllegalArgumentException();
    }
    Resource qswebxml = dir.addPath("/WEB-INF/quickstart-web.xml");
    if (qswebxml.exists()) {
      setConfigurationClasses(quickstartConfigurationClasses);
    }
    super.doStart();
  }
  /**
   * Creates a List of SessionStores based on the configuration in the provided AppEngineWebXml.
   *
   * @param appEngineWebXml The AppEngineWebXml containing the session configuration.
   * @return A List of SessionStores in write order.
   */
  private static List<SessionStore> createSessionStores(AppEngineWebXml appEngineWebXml) {
    DatastoreSessionStore datastoreSessionStore =
        appEngineWebXml.getAsyncSessionPersistence() ? new DeferredDatastoreSessionStore(
            appEngineWebXml.getAsyncSessionPersistenceQueueName())
            : new DatastoreSessionStore();
    // Write session data to the datastore before we write to memcache.
    return Arrays.asList(datastoreSessionStore, new MemcacheSessionStore());
  }

  /**
   * Checks if the request was made over HTTPS. If so it modifies the request so that
   * {@code HttpServletRequest#isSecure()} returns true, {@code HttpServletRequest#getScheme()}
   * returns "https", and {@code HttpServletRequest#getServerPort()} returns 443. Otherwise it sets
   * the scheme to "http" and port to 80.
   *
   * @param request The request to modify.
   */
  private void setSchemeAndPort(Request request) {
    String https = request.getHeader(VmApiProxyEnvironment.HTTPS_HEADER);
    if ("on".equals(https)) {
      request.setSecure(true);
      request.setScheme(HttpScheme.HTTPS.toString());
      request.setServerPort(443);
    } else {
      request.setSecure(false);
      request.setScheme(HttpScheme.HTTP.toString());
      request.setServerPort(defaultEnvironment.getServerPort());
    }
  }

  /**
   * Creates a new VmRuntimeWebAppContext.
   */
  public VmRuntimeWebAppContext() {
    this.serverInfo = VmRuntimeUtils.getServerInfo();
    _scontext = new VmRuntimeServletContext();

    // Configure the Jetty SecurityHandler to understand our method of authentication
    // (via the UserService). Only the default ConstraintSecurityHandler is supported.
    AppEngineAuthentication.configureSecurityHandler(
        (ConstraintSecurityHandler) getSecurityHandler(), this);

    setMaxFormContentSize(MAX_RESPONSE_SIZE);
    setConfigurationClasses(preconfigurationClasses);
    // See http://www.eclipse.org/jetty/documentation/current/configuring-webapps.html#webapp-context-attributes
    // We also want the Jetty container libs to be scanned for annotations.
    setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar");
    metadataCache = new VmMetadataCache();
    wallclockTimer = new VmTimer();
    ApiProxy.setDelegate(new VmApiProxyDelegate());
  }

  /**
   * Initialize the WebAppContext for use by the VmRuntime.
   *
   * This method initializes the WebAppContext by setting the context path and application folder.
   * It will also parse the appengine-web.xml file provided to set System Properties and session
   * manager accordingly.
   *
   * @param appDir The war directory of the application.
   * @param appengineWebXmlFile The appengine-web.xml file path (relative to appDir).
   * @throws AppEngineConfigException If there was a problem finding or parsing the
   *         appengine-web.xml configuration.
   * @throws IOException If the runtime was unable to find/read appDir.
   */
  public void init(String appDir, String appengineWebXmlFile)
      throws AppEngineConfigException, IOException {
    setContextPath("/");
    setWar(appDir);
    setResourceBase(appDir);
    defaultEnvironment = VmApiProxyEnvironment.createDefaultContext(
        System.getenv(), metadataCache, VmRuntimeUtils.getApiServerAddress(), wallclockTimer,
        VmRuntimeUtils.ONE_DAY_IN_MILLIS, new File(appDir).getCanonicalPath());
    ApiProxy.setEnvironmentForCurrentThread(defaultEnvironment);
    if (ApiProxy.getEnvironmentFactory() == null) {
      // Need the check above since certain unit tests initialize the context multiple times.
      ApiProxy.setEnvironmentFactory(new VmEnvironmentFactory(defaultEnvironment));
    }

    isDevMode = defaultEnvironment.getPartition().equals("dev");
    AppEngineWebXmlReader appEngineWebXmlReader =
        new AppEngineWebXmlReader(appDir, appengineWebXmlFile);
    AppEngineWebXml appEngineWebXml = appEngineWebXmlReader.readAppEngineWebXml();
    VmRuntimeUtils.installSystemProperties(defaultEnvironment, appEngineWebXml);
    VmRuntimeLogHandler.init();
    VmRuntimeFileLogHandler.init();

    for (String systemClass : SYSTEM_CLASSES) {
      addSystemClass(systemClass);
    }

    AbstractSessionManager sessionManager;
    if (appEngineWebXml.getSessionsEnabled()) {
      sessionManager = new SessionManager(createSessionStores(appEngineWebXml));
    } else {
      sessionManager = new StubSessionManager();
    }
    setSessionHandler(new SessionHandler(sessionManager));
  }

  @Override
  public boolean isTrustedRemoteAddr(String remoteAddr) {
    return VmRequestUtils.isTrustedRemoteAddr(isDevMode, remoteAddr);
  }

  /**

   * Overrides doScope from ScopedHandler.
   *
   *  Configures a thread local environment before the request is forwarded on to be handled by the
   * SessionHandler, SecurityHandler, and ServletHandler in turn. The environment is required for
   * AppEngine APIs to function. A request specific environment is required since some information
   * is encoded in request headers on the request (for example current user).
   */
  @Override
  public final void doScope(
      String target, Request baseRequest, HttpServletRequest httpServletRequest ,
      HttpServletResponse httpServletResponse)
      throws IOException, ServletException {

    HttpRequest request = new HttpServletRequestAdapter(httpServletRequest);
    HttpResponse response = new HttpServletResponseAdapter(httpServletResponse);

    // For JSP Includes do standard processing, everything else has been done
    // in the main request before the include.
    if (DispatcherType.INCLUDE.equals(httpServletRequest.getDispatcherType())
        || DispatcherType.FORWARD.equals(httpServletRequest.getDispatcherType())) {
      super.doScope(target, baseRequest, httpServletRequest, httpServletResponse);
      return;
    }
    // Install a thread local environment based on request headers of the current request.
    VmApiProxyEnvironment requestSpecificEnvironment = VmApiProxyEnvironment.createFromHeaders(
        System.getenv(), metadataCache, request, VmRuntimeUtils.getApiServerAddress(),
        wallclockTimer, VmRuntimeUtils.ONE_DAY_IN_MILLIS, defaultEnvironment);
    CommitDelayingResponseServlet3 wrappedResponse;
    if (httpServletResponse instanceof CommitDelayingResponseServlet3) {
      wrappedResponse = (CommitDelayingResponseServlet3) httpServletResponse;
    } else {
      wrappedResponse = new CommitDelayingResponseServlet3(httpServletResponse);
    }

    if (httpServletResponse instanceof org.eclipse.jetty.server.Response) {
      // The jetty 9.1 HttpOutput class has logic to commit the stream when it reaches a certain
      // threshold.  Inexplicably, by default, that threshold is set to one-fourth its buffer size.
      // That defeats the purpose of our commit delaying response.  Luckily, setting the buffer
      // size again sets the commit size to same value.
      // See go/jetty9-httpoutput.java for the relevant jetty source code.
      ((org.eclipse.jetty.server.Response) httpServletResponse).getHttpOutput().setBufferSize(
          wrappedResponse.getBufferSize());
    }
    try {
      ApiProxy.setEnvironmentForCurrentThread(requestSpecificEnvironment);
      // Check for SkipAdminCheck and set attributes accordingly.
      VmRuntimeUtils.handleSkipAdminCheck(request);
      // Change scheme to HTTPS based on headers set by the appserver.
      setSchemeAndPort(baseRequest);
      // Forward the request to the rest of the handlers.
      super.doScope(target, baseRequest, httpServletRequest, wrappedResponse);
    } finally {
      try {
        // Interrupt any remaining request threads and wait for them to complete.
        VmRuntimeUtils.interruptRequestThreads(
            requestSpecificEnvironment, VmRuntimeUtils.MAX_REQUEST_THREAD_INTERRUPT_WAIT_TIME_MS);
        // Wait for any pending async API requests to complete.
        if (!VmRuntimeUtils.waitForAsyncApiCalls(requestSpecificEnvironment,
            new HttpServletResponseAdapter(wrappedResponse))) {
          logger.warning("Timed out or interrupted while waiting for async API calls to complete.");
        }
        if (!response.isCommitted()) {
          // Flush and set the flush count header so the appserver knows when all logs are in.
          VmRuntimeUtils.flushLogsAndAddHeader(response, requestSpecificEnvironment);
        } else {
          throw new ServletException("Response for request to '" + target
              + "' was already commited (code=" + httpServletResponse.getStatus()
              + "). This might result in lost log messages.'");
        }
      } finally {
        try {
          // Complete any pending actions.
          wrappedResponse.commit();
        } finally {
          // Restore the default environment.
          ApiProxy.setEnvironmentForCurrentThread(defaultEnvironment);
        }
      }
    }
  }

  // N.B.(schwardo): Yuck. Jetty hardcodes all of this logic into an
  // inner class of ContextHandler. We need to subclass WebAppContext
  // (which extends ContextHandler) and then subclass the SContext
  // inner class to modify its behavior.
  /**
   * ServletContext for VmRuntime applications.
   */
  public class VmRuntimeServletContext extends Context {

    @Override
    public ClassLoader getClassLoader() {
      return VmRuntimeWebAppContext.this.getClassLoader();
    }

    @Override
    public String getServerInfo() {
      return serverInfo;
    }

    @Override
    public void log(String message) {
      log(message, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param throwable an exception associated with this log message,
     * or {@code null}.
     */
    @Override
    public void log(String message, Throwable throwable) {
      StringWriter writer = new StringWriter();
      writer.append("javax.servlet.ServletContext log: ");
      writer.append(message);

      if (throwable != null) {
        writer.append("\n");
        throwable.printStackTrace(new PrintWriter(writer));
      }

      LogRecord.Level logLevel = throwable == null ? LogRecord.Level.info : LogRecord.Level.error;
      ApiProxy.log(new ApiProxy.LogRecord(logLevel, System.currentTimeMillis() * 1000L,
          writer.toString()));
    }

    @Override
    public void log(Exception exception, String msg) {
      log(msg, exception);
    }
  }
}

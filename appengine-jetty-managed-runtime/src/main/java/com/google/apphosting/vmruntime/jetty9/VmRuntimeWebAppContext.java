/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.apphosting.vmruntime.jetty9;

import static com.google.appengine.repackaged.com.google.common.base.MoreObjects.firstNonNull;

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

/**
 * WebAppContext for VM Runtimes. This class extends the "normal" AppEngineWebAppContext with
 * functionality that installs a request specific thread local environment on each incoming request.
 */
public class VmRuntimeWebAppContext
        extends WebAppContext implements VmRuntimeTrustedAddressChecker {

  private static final Logger logger = Logger.getLogger(VmRuntimeWebAppContext.class.getName());

  private static final String[] SYSTEM_CLASSES = {
    "com.google.appengine.api.",
    "com.google.appengine.tools.",
    "com.google.apphosting.",
    "com.google.cloud.sql.jdbc.",
    "com.google.protos.cloud.sql.",
    "com.google.storage.onestore.",};
  protected static final int MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  private final String serverInfo;

  private final VmMetadataCache metadataCache;
  private final Timer wallclockTimer;
  private VmApiProxyEnvironment defaultEnvironment;

  boolean isDevMode;

  static {
    System.setProperty(
            ServiceFactoryFactory.USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY, Boolean.TRUE.toString());
    System.setProperty(
            MemcacheSerialization.USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY, Boolean.TRUE.toString());
  }

  private static final String[] quickstartConfigurationClasses = {
    org.eclipse.jetty.quickstart.QuickStartConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.JettyWebXmlConfiguration.class.getCanonicalName()
  };

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
   * Creates a List of SessionStores based on the configuration in the provided
   * AppEngineWebXml.
   *
   * @param appEngineWebXml The AppEngineWebXml containing the session
   * configuration.
   * @return A List of SessionStores in write order.
   */
  private static List<SessionStore> createSessionStores(AppEngineWebXml appEngineWebXml) {
    DatastoreSessionStore datastoreSessionStore
            = appEngineWebXml.getAsyncSessionPersistence() ? new DeferredDatastoreSessionStore(
                            appEngineWebXml.getAsyncSessionPersistenceQueueName())
                    : new DatastoreSessionStore();
    return Arrays.asList(datastoreSessionStore, new MemcacheSessionStore());
  }

  /**
   * Checks if the request was made over HTTPS. If so it modifies the request so
   * that {@code HttpServletRequest#isSecure()} returns true,
   * {@code HttpServletRequest#getScheme()} returns "https", and
   * {@code HttpServletRequest#getServerPort()} returns 443. Otherwise it sets
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
     // request.setAuthority(request.getServerName(),443);

    } else {
      request.setSecure(false);
      request.setScheme(HttpScheme.HTTP.toString());
      request.setServerPort(defaultEnvironment.getServerPort());
     // request.setAuthority(request.getServerName(),defaultEnvironment.getServerPort());
    }
  }

  /**
   * Creates a new VmRuntimeWebAppContext.
   */
  public VmRuntimeWebAppContext() {
    this.serverInfo = VmRuntimeUtils.getServerInfo();
    _scontext = new VmRuntimeServletContext();

    AppEngineAuthentication.configureSecurityHandler(
            (ConstraintSecurityHandler) getSecurityHandler(), this);

    setMaxFormContentSize(MAX_RESPONSE_SIZE);
    setConfigurationClasses(preconfigurationClasses);
    setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar");
    metadataCache = new VmMetadataCache();
    wallclockTimer = new VmTimer();
    ApiProxy.setDelegate(new VmApiProxyDelegate());
  }

  /**
   * Initialize the WebAppContext for use by the VmRuntime.
   *
   * This method initializes the WebAppContext by setting the context path and
   * application folder. It will also parse the appengine-web.xml file provided
   * to set System Properties and session manager accordingly.
   *
   * @param appDir The war directory of the application.
   * @param appengineWebXmlFile The appengine-web.xml file path (relative to
   * appDir).
   * @throws AppEngineConfigException If there was a problem finding or parsing
   * the appengine-web.xml configuration.
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
      ApiProxy.setEnvironmentFactory(new VmEnvironmentFactory(defaultEnvironment));
    }

    isDevMode = defaultEnvironment.getPartition().equals("dev");
    AppEngineWebXmlReader appEngineWebXmlReader
            = new AppEngineWebXmlReader(appDir, appengineWebXmlFile);
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

    int checkIntervalSec = firstNonNull(appEngineWebXml.getHealthCheck().getCheckIntervalSec(),
        VmRequestUtils.DEFAULT_CHECK_INTERVAL_SEC);
    if (checkIntervalSec <= 0) {
      logger.warning(
          "health check interval is not positive: " + checkIntervalSec
          + ". Using default value: " + VmRequestUtils.DEFAULT_CHECK_INTERVAL_SEC);
      checkIntervalSec = VmRequestUtils.DEFAULT_CHECK_INTERVAL_SEC;
    }
    VmRequestUtils.setCheckIntervalSec(checkIntervalSec);
  }

  public boolean isTrustedRemoteAddr(String remoteAddr) {
    return VmRequestUtils.isTrustedRemoteAddr(isDevMode, remoteAddr);
  }

  /**
   * Overrides doScope from ScopedHandler.
   *
   * Configures a thread local environment before the request is forwarded on to
   * be handled by the SessionHandler, SecurityHandler, and ServletHandler in
   * turn. The environment is required for AppEngine APIs to function. A request
   * specific environment is required since some information is encoded in
   * request headers on the request (for example current user).
   */
  @Override
  public final void doScope(
          String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
    String remoteAddr
            = baseRequest.getHttpChannel().getEndPoint().getRemoteAddress().getAddress().getHostAddress();

    if (VmRequestUtils.isHealthCheck(request)) {
      if (!VmRequestUtils.isValidHealthCheckAddr(isDevMode, remoteAddr)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "403 Forbidden");
        return;
      }

      if (VmRequestUtils.isLocalHealthCheck(request, remoteAddr)) {
        VmRequestUtils.handleLocalHealthCheck(response);
        return;
      } else {
        VmRequestUtils.recordLastNormalHealthCheckStatus(request);
      }
    }
    // For JSP Includes do standard processing, everything else has been done
    // in the main request before the include.
    if (DispatcherType.INCLUDE.equals(request.getDispatcherType()) ||
         DispatcherType.FORWARD.equals(request.getDispatcherType())) {
      super.doScope(target, baseRequest, request, response);
      return;     
    }
    VmApiProxyEnvironment requestSpecificEnvironment = VmApiProxyEnvironment.createFromHeaders(
            System.getenv(), metadataCache, request, VmRuntimeUtils.getApiServerAddress(),
            wallclockTimer, VmRuntimeUtils.ONE_DAY_IN_MILLIS, defaultEnvironment);
    CommitDelayingResponseServlet3 wrappedResponse = null;
    if (response instanceof CommitDelayingResponseServlet3) {
      wrappedResponse = (CommitDelayingResponseServlet3) response;
    } else {
      wrappedResponse = new CommitDelayingResponseServlet3(response);
    }
    if (response instanceof org.eclipse.jetty.server.Response) {
      ((org.eclipse.jetty.server.Response) response).getHttpOutput().setBufferSize(
              wrappedResponse.getBufferSize());
    }
    try {
      ApiProxy.setEnvironmentForCurrentThread(requestSpecificEnvironment);
      VmRuntimeUtils.handleSkipAdminCheck(request);
      setSchemeAndPort(baseRequest);
      super.doScope(target, baseRequest, request, wrappedResponse);
    } finally {
      try {
        VmRuntimeUtils.interruptRequestThreads(
                requestSpecificEnvironment, VmRuntimeUtils.MAX_REQUEST_THREAD_INTERRUPT_WAIT_TIME_MS);
        if (!VmRuntimeUtils.waitForAsyncApiCalls(requestSpecificEnvironment, wrappedResponse)) {
          logger.warning("Timed out or interrupted while waiting for async API calls to complete.");
        }
        if (!response.isCommitted()) {
          VmRuntimeUtils.flushLogsAndAddHeader(response, requestSpecificEnvironment);
        } else {
          //noinspection ThrowFromFinallyBlock
          throw new ServletException("Response for request to '" + target
                  + "' was already commited (code=" + response.getStatus()
                  + "). This might result in lost log messages.'");
        }
      } finally {
        try {
          wrappedResponse.commit();
        } finally {
          ApiProxy.setEnvironmentForCurrentThread(defaultEnvironment);
        }
      }
    }
  }

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
     * @param throwable an exception associated with this log message, or
     * {@code null}.
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

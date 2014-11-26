/**
 * Copyright 2014 Google Inc. All Rights Reserved.
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

import static com.google.appengine.repackaged.com.google.common.base.MoreObjects.firstNonNull;

import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.spi.ServiceFactoryFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.jetty9.DatastoreSessionStore;
import com.google.apphosting.runtime.jetty9.DeferredDatastoreSessionStore;
import com.google.apphosting.runtime.jetty9.MemcacheSessionStore;
import com.google.apphosting.runtime.jetty9.SessionManager;
import com.google.apphosting.runtime.timer.Timer;
import com.google.apphosting.utils.http.HttpRequest;
import com.google.apphosting.utils.http.HttpResponse;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.jetty9.AppEngineWebAppContext;
import com.google.apphosting.utils.jetty9.StubSessionManager;
import com.google.apphosting.utils.servlet.HttpServletRequestAdapter;
import com.google.apphosting.utils.servlet.HttpServletResponseAdapter;
import com.google.apphosting.vmruntime.CommitDelayingResponseServlet3;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;
import com.google.apphosting.vmruntime.VmMetadataCache;
import com.google.apphosting.vmruntime.VmRuntimeFileLogHandler;
import com.google.apphosting.vmruntime.VmRuntimeLogHandler;
import com.google.apphosting.vmruntime.VmRuntimeUtils;
import com.google.apphosting.vmruntime.VmTimer;


import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * WebAppContext for VM Runtimes. This class extends the "normal" AppEngineWebAppContext with
 * functionality that installs a request specific thread local environment on each incoming request.
 */
public class VmRuntimeWebAppContext extends AppEngineWebAppContext {
  private static final Logger logger = Logger.getLogger(VmRuntimeWebAppContext.class.getName());

  private static final String[] SYSTEM_CLASSES = {
    "com.google.appengine.api.",
    "com.google.appengine.tools.",
    "com.google.apphosting.",
    "com.google.cloud.sql.jdbc.",
    "com.google.protos.cloud.sql.",
    "com.google.storage.onestore.",
  };

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

  private static final String HEALTH_CHECK_PATH = "/_ah/health";
  
  static final double HEALTH_CHECK_INTERVAL_OFFSET_RATIO = 1.5;
  private static boolean isLastSuccessful = false;
  private static long timeStampOfLastNormalCheckMillis = 0;
  
  static int checkIntervalSec = -1;
  static final int DEFAULT_CHECK_INTERVAL_SEC = 5;
  static final String LINK_LOCAL_IP_NETWORK = "169.254";

  private static final String[] quickstartConfigurationClasses  = {
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
    super(VmRuntimeUtils.getServerInfo());
    setConfigurationClasses(preconfigurationClasses);
    setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar");
    metadataCache = new VmMetadataCache();
    wallclockTimer = new VmTimer();
    ApiProxy.setDelegate(new VmApiProxyDelegate());
  }

  /**
   * Simple implementation of ApiProxy.EnvironmentFactory. It just returns the default environment,
   * with a thread local copy of the attributes, since they are mutable.
   */
  
  public static class VmEnvironmentFactory implements ApiProxy.EnvironmentFactory {
    private final VmApiProxyEnvironment defaultEnvironment;

    public VmEnvironmentFactory(VmApiProxyEnvironment defaultEnvironment) {
      this.defaultEnvironment = defaultEnvironment;
    }

    @Override
    public Environment newEnvironment() {
      defaultEnvironment.setThreadLocalAttributes();
      return defaultEnvironment;
    }
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

    checkIntervalSec = firstNonNull(appEngineWebXml.getHealthCheck().getCheckIntervalSec(),
        DEFAULT_CHECK_INTERVAL_SEC);
    if (checkIntervalSec <= 0) {
      logger.warning(
          "health check interval is not positive: " + checkIntervalSec
          + ". Using default value: " + DEFAULT_CHECK_INTERVAL_SEC);
      checkIntervalSec = DEFAULT_CHECK_INTERVAL_SEC;
    }
  }

  /**
   * Checks if a remote address is trusted for the purposes of handling requests.
   *
   * @param remoteAddr String representation of the remote ip address.
   * @returns True if and only if the remote address should be allowed to make requests.
   */
  protected boolean isValidRemoteAddr(String remoteAddr) {
    if (isDevMode) {
      return true;
    } else if (remoteAddr.startsWith("172.17.")) {
      return true;
    } else if (remoteAddr.startsWith(LINK_LOCAL_IP_NETWORK)) {
      return true;
    } else if (remoteAddr.startsWith("127.0.0.")) {
      return true;
    }
    return false;
  }

  private static boolean isHealthCheck(HttpRequest request) {
    if (HEALTH_CHECK_PATH.equalsIgnoreCase(request.getPathInfo())) {
      return true;
    }
    return false;
  }

  private static boolean isLocalHealthCheck(HttpRequest request, String remoteAddr) {
    String isLastSuccessfulPara = request.getParameter("IsLastSuccessful");
    if (isLastSuccessfulPara == null && !remoteAddr.startsWith(LINK_LOCAL_IP_NETWORK)) {
      return true;
    }
    return false;
  }

  /**
   * Record last normal health check status. It sets this.isLastSuccessful based on the value of
   * "IsLastSuccessful" parameter from the query string ("yes" for True, otherwise False), and also
   * updates this.timeStampOfLastNormalCheckMillis.
   *
   * @param request the HttpServletRequest
   */
  private static void recordLastNormalHealthCheckStatus(HttpRequest request) {
    String isLastSuccessfulPara = request.getParameter("IsLastSuccessful");
    if ("yes".equalsIgnoreCase(isLastSuccessfulPara)) {
      isLastSuccessful = true;
    } else if ("no".equalsIgnoreCase(isLastSuccessfulPara)) {
      isLastSuccessful = false;
    } else {
      isLastSuccessful = false;
      logger.warning("Wrong parameter for IsLastSuccessful: " + isLastSuccessfulPara);
    }

    timeStampOfLastNormalCheckMillis = System.currentTimeMillis();
  }

  /**
   * Handle local health check from within the VM. If there is no previous normal check or that
   * check has occurred more than checkIntervalSec seconds ago, it returns unhealthy. Otherwise,
   * returns status based value of this.isLastSuccessful, "true" for success and "false" for
   * failure.
   *
   * @param response the HttpServletResponse
   * @throws IOException when it couldn't send out response
   */
  private static void handleLocalHealthCheck(HttpResponse response) throws IOException {
    if (!isLastSuccessful) {
      logger.warning("unhealthy (isLastSuccessful is False)");
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    if (timeStampOfLastNormalCheckMillis == 0) {
      logger.warning("unhealthy (no incoming remote health checks seen yet)");
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    long timeOffset = System.currentTimeMillis() - timeStampOfLastNormalCheckMillis;
    if (timeOffset > checkIntervalSec * HEALTH_CHECK_INTERVAL_OFFSET_RATIO * 1000) {
      logger.warning("unhealthy (last incoming health check was " + timeOffset + "ms ago)");
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    response.setContentType("text/plain");
    response.write("ok");
    response.setStatus(HttpServletResponse.SC_OK);
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
      String target, Request baseRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws IOException, ServletException {
    HttpRequest request = new HttpServletRequestAdapter(httpServletRequest);
    HttpResponse response = new HttpServletResponseAdapter(httpServletResponse);

    String remoteAddr =
        baseRequest.getHttpChannel().getEndPoint().getRemoteAddress().getAddress().getHostAddress();
    if (!isValidRemoteAddr(remoteAddr)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "403 Forbidden");
      return;
    }

    if (isHealthCheck(request)) {
      if (isLocalHealthCheck(request, remoteAddr)) {
        handleLocalHealthCheck(response);
        return;
      } else {
        recordLastNormalHealthCheckStatus(request);
      }
    }

    VmApiProxyEnvironment requestSpecificEnvironment = VmApiProxyEnvironment.createFromHeaders(
        System.getenv(), metadataCache, request, VmRuntimeUtils.getApiServerAddress(),
        wallclockTimer, VmRuntimeUtils.ONE_DAY_IN_MILLIS, defaultEnvironment);

    CommitDelayingResponseServlet3 wrappedResponse = new CommitDelayingResponseServlet3(httpServletResponse);

    if (httpServletResponse instanceof org.eclipse.jetty.server.Response) {
      ((org.eclipse.jetty.server.Response) httpServletResponse).getHttpOutput().setBufferSize(
          wrappedResponse.getBufferSize());
    }
    try {
      ApiProxy.setEnvironmentForCurrentThread(requestSpecificEnvironment);
      VmRuntimeUtils.handleSkipAdminCheck(request);
      setSchemeAndPort(baseRequest);
      super.doScope(target, baseRequest, httpServletRequest, wrappedResponse);
    } finally {
      try {
        VmRuntimeUtils.interruptRequestThreads(
            requestSpecificEnvironment, VmRuntimeUtils.MAX_REQUEST_THREAD_INTERRUPT_WAIT_TIME_MS);
        if (!VmRuntimeUtils.waitForAsyncApiCalls(requestSpecificEnvironment, new HttpServletResponseAdapter(wrappedResponse))) {
          logger.warning("Timed out or interrupted while waiting for async API calls to complete.");
        }
        if (!response.isCommitted()) {
          VmRuntimeUtils.flushLogsAndAddHeader(response, requestSpecificEnvironment);
        } else {
          throw new ServletException("Response for request to '" + target
              + "' was already commited (code=" + ((Response) response).getStatus()
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
}

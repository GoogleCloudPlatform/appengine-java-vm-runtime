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

package com.google.apphosting.vmruntime;

import static com.google.appengine.repackaged.com.google.common.base.MoreObjects.firstNonNull;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.base.AppId;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.http.HttpRequest;
import com.google.apphosting.utils.http.HttpResponse;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constants and utility functions shared by the Jetty 6 and Jetty 9 VmRuntimeWebAppContext.
 *
 */
public class VmRuntimeUtils {
  // This should be kept in sync with HTTPProto::X_GOOGLE_INTERNAL_SKIPADMINCHECK.
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "X-Google-Internal-SkipAdminCheck";
  // This should be kept in sync with HTTPProto::X_APPENGINE_QUEUENAME.
  private static final String X_APPENGINE_QUEUENAME = "X-AppEngine-QueueName";
  // Keep in sync with com.google.apphosting.utils.jetty[8].AppEngineAuthentication.
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  private static final String VM_API_PROXY_HOST = "appengine.googleapis.internal";
  private static final int VM_API_PROXY_PORT = 10001;

  public static final long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

  public static final long MAX_REQUEST_THREAD_INTERRUPT_WAIT_TIME_MS = 10;
  public static final long MAX_REQUEST_THREAD_API_CALL_WAIT_MS = 30 * 1000;
  public static final long MAX_USER_API_CALL_WAIT_MS = 60 * 1000;
  // Keep in sync with apphosting/base/http_proto.cc
  public static final String LOG_FLUSH_COUNTER_HEADER = "X-AppEngine-Log-Flush-Count";

  public static final String ASYNC_API_WAIT_HEADER = "X-AppEngine-Async-Api-Wait";

  private static final String MINOR_VERSION_PATTERN = "/home/vmagent/.+_%s-([0-9]+)/root";

  /**
   * Reads the AppEngine release version from the package specification in the jar file.
   *
   * @return The AppEngine version (for example 1.7.4), or "unknown" if the class is loaded outside
   *         appengine-vm-runtime.jar (for example during tests).
   */
  private static String getAppEngineRelease() {
    Package vmruntimePackage = VmRuntimeUtils.class.getPackage();
    if (vmruntimePackage == null || vmruntimePackage.getSpecificationVersion() == null) {
      return "unknown";
    }
    return vmruntimePackage.getSpecificationVersion();
  }

  /**
   * Extract the minor version based on the canonical path of the app.
   *
   * @param majorVersion The current major version.
   * @param path The current resource base.
   * @return The minor version.
   */
  public static String getMinorVersionFromPath(String majorVersion, String path) {
    Pattern pattern = Pattern.compile(String.format(MINOR_VERSION_PATTERN, majorVersion));
    Matcher matcher = pattern.matcher(path);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return "0";
  }

  /**
   * Creates the server info string that should be returned by ServletContext.getServerInfo().
   *
   * See //java/com/google/apphosting/runtime/JavaRuntime.java:runtimeVersion
   */
  public static String getServerInfo() {
    return "Google App Engine/" + getAppEngineRelease();
  }

  /**
   * Initiates a synchronous log flush and inserts the flush count header into the response. The
   * header is added so that the appserver can know when all log API calls are completed and can
   * delay the request accordingly.
   *
   * @param response The response to add the flush count header to.
   * @param requestSpecificEnvironment The environment used by the request.
   */
  public static void flushLogsAndAddHeader(
      HttpResponse response, VmApiProxyEnvironment requestSpecificEnvironment) {
    int flushCount = requestSpecificEnvironment.flushLogs();
    response.setHeader(VmRuntimeUtils.LOG_FLUSH_COUNTER_HEADER, Integer.toString(flushCount));
  }

  /**
   * Check if the request has the internal "skip admin check" header or comes from a task queue, if
   * so set a request attribute so this information can be used by the security handler.
   *
   * @param request The request to inspect and modify.
   */
  public static void handleSkipAdminCheck(HttpRequest request) {
    if (request.getHeader(VmRuntimeUtils.X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null
        || request.getHeader(VmRuntimeUtils.X_APPENGINE_QUEUENAME) != null) {
      request.setAttribute(VmRuntimeUtils.SKIP_ADMIN_CHECK_ATTR, Boolean.TRUE);
    }
  }

  /**
   * Set the Environment system property to Development or Production.
   */
  static void setEnvironmentSystemProperty(String partition) {
    if ("dev".equals(partition)) {
      System.setProperty(
          SystemProperty.environment.key(), SystemProperty.Environment.Value.Development.name());
    } else {
      System.setProperty(
          SystemProperty.environment.key(), SystemProperty.Environment.Value.Production.name());
    }
  }

  /**
   * Install system properties based on the provided VmApiProxyEnvironment.
   */
  public static void installSystemProperties(
      VmApiProxyEnvironment environment, AppEngineWebXml appEngineWebXml) {
    setEnvironmentSystemProperty(environment.getPartition());
    System.setProperty(SystemProperty.version.key(), getServerInfo());
    System.setProperty(
        SystemProperty.applicationId.key(), AppId.parse(environment.getAppId()).getLongAppId());
    System.setProperty(SystemProperty.applicationVersion.key(), environment.getVersionId());
    System.setProperty("appengine.jetty.also_log_to_apiproxy", "true");
    if (appEngineWebXml != null) {
      for (Map.Entry<String, String> entry : appEngineWebXml.getSystemProperties().entrySet()) {
        System.setProperty(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Interrupt any request threads created by the current request.
   *
   * @param requestEnvironment A request specific environment containing the thread factory that
   *        created the threads.
   * @param millis Wait up to @code{millis} milliseconds for all request threads to die.
   */
  public static void interruptRequestThreads(
      VmApiProxyEnvironment requestEnvironment, long millis) {
    Object threadFactory =
        requestEnvironment.getAttributes().get(VmApiProxyEnvironment.REQUEST_THREAD_FACTORY_ATTR);
    if (threadFactory != null && threadFactory instanceof VmRequestThreadFactory) {
      VmRequestThreadFactory vmRequestThreadFactory = (VmRequestThreadFactory) threadFactory;
      vmRequestThreadFactory.interruptRequestThreads();
      vmRequestThreadFactory.join(millis);
    }
  }

  /**
   * Waits for all Async API calls made with the provided environment to complete and injects the
   * number of milliseconds it took into a header of the response.
   *
   * @param requestEnvironment The request specific API environment.
   * @param response The response to add header to.
   * @return True if all calls completed before the timeout fired or the thread was interrupted.
   *         False otherwise.
   */
  @Deprecated
  public static boolean waitForAsyncApiCalls(
      VmApiProxyEnvironment requestEnvironment, HttpResponse response) {
    long startTime = System.currentTimeMillis();
    boolean success =
        requestEnvironment.waitForAllApiCallsToComplete(MAX_REQUEST_THREAD_API_CALL_WAIT_MS);
    long elapsed = System.currentTimeMillis() - startTime;
    response.setHeader(ASYNC_API_WAIT_HEADER, Long.toString(elapsed));
    return success;
  }

  /**
   * Returns the host:port of the API server.
   *
   * @return If environment variables API_HOST or API_PORT port are set the host and/or port is
   *         calculated from them. Otherwise the default host:port is used.
   */
  public static String getApiServerAddress() {
    String server = firstNonNull(System.getenv("API_HOST"), VM_API_PROXY_HOST);
    String port = firstNonNull(System.getenv("API_PORT"), "" + VM_API_PROXY_PORT);
    return server + ":" + port;
  }
}

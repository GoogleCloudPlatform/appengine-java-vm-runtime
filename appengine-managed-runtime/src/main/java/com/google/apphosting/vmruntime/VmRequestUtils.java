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

package com.google.apphosting.vmruntime;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * VM request utils.
 */
public class VmRequestUtils {
  private static final Logger logger = Logger.getLogger(VmRequestUtils.class.getName());

  private static final String HEALTH_CHECK_PATH = "/_ah/health";

  public static final double HEALTH_CHECK_INTERVAL_OFFSET_RATIO = 1.5;
  public static final int DEFAULT_CHECK_INTERVAL_SEC = 5;
  public static final String LINK_LOCAL_IP_NETWORK = "169.254";

  // For better understanding of isLastSuccessful and timeStampOfLastNormalCheckMillis, please see
  // Keep the last remote health check status from AppServer.
  private static boolean isLastSuccessful = false;
  // The time stamp of last normal health check, in milliseconds.
  private static long timeStampOfLastNormalCheckMillis = 0;
  private static int checkIntervalSec = -1;

  /**
   * Set the value in seconds of the interval to determine if a health check
   * request is valid.
   */
  public static void setCheckIntervalSec(int checkIntervalSec) {
    VmRequestUtils.checkIntervalSec = checkIntervalSec;
  }

  /**
   *
   * @return the interval in seconds to determine if a health check
   * request is valid.
   */
  public static int getCheckIntervalSec() {
    return checkIntervalSec;
  }

  /**
   * Checks if a remote address is trusted for the purposes of handling requests.
   *
   * @param remoteAddr String representation of the remote ip address.
   * @return True if and only if the remote address should be allowed to make requests.
   */
  public static boolean isTrustedRemoteAddr(boolean isDevMode, String remoteAddr) {
    if (isDevMode) {
      return isDevMode;
    } else if (remoteAddr == null) {
      return false;
    } else if (remoteAddr.startsWith("172.17.")) {
      // Allow traffic from default docker ip bride range (172.17.0.0/16)
      return true;
    } else if (remoteAddr.startsWith(LINK_LOCAL_IP_NETWORK)) {
      return true;
    } else if (remoteAddr.startsWith("127.0.0.")) {
      // Allow localhost.
      return true;
    }
    return false;
  }

  /**
   * Call when this is a health check request, to determine if this is a
   * valid health check address, coming from GCLB.
   * @param isDevMode: Running in the SDK (dev) or in Prod.
   * @param remoteAddr GCLB address, serving Health checks, or null.
   * @return true if the health check address is valid.
   */
  public static boolean isValidHealthCheckAddr(boolean isDevMode, String remoteAddr) {
    if (isTrustedRemoteAddr(isDevMode, remoteAddr)) {
      return true;
    } else if (remoteAddr == null) {
      return false;
    }
    // Health checks need to be served from GCLB.
    return remoteAddr.startsWith("130.211.0.")
        || remoteAddr.startsWith("130.211.1.")
        || remoteAddr.startsWith("130.211.2.")
        || remoteAddr.startsWith("130.211.3.");
  }

  /**
   * @param request a servlet request.
   * @return true if the request is a health check request (i.e
   * "/_ah/health").
   */
  public static boolean isHealthCheck(HttpServletRequest request) {
    return HEALTH_CHECK_PATH.equalsIgnoreCase(request.getPathInfo());
  }

  /**
   * @param request a servlet request.
   * @param remoteAddr: the address of the health check caller.
   * @return true if this is a local health check from the VM.
   */
  public static boolean isLocalHealthCheck(HttpServletRequest request, String remoteAddr) {
    String isLastSuccessfulPara = request.getParameter("IsLastSuccessful");
    return isLastSuccessfulPara == null && !remoteAddr.startsWith(LINK_LOCAL_IP_NETWORK);
  }

  /**
   * Record last normal health check status. It sets this.isLastSuccessful based on the value of
   * "IsLastSuccessful" parameter from the query string ("yes" for True, otherwise False), and also
   * updates this.timeStampOfLastNormalCheckMillis.
   *
   * @param request the HttpServletRequest
   */
  public static void recordLastNormalHealthCheckStatus(HttpServletRequest request) {
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
  public static void handleLocalHealthCheck(HttpServletResponse response) throws IOException {
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
    // All good! Send ok
    response.setContentType("text/plain");
    PrintWriter writer = response.getWriter();
    writer.write("ok");
    // Calling flush() on the PrintWriter commits the response.
    writer.flush();
    response.setStatus(HttpServletResponse.SC_OK);
  }
}

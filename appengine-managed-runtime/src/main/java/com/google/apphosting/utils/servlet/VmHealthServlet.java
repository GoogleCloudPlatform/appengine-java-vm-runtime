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

package com.google.apphosting.utils.servlet;

import com.google.appengine.api.LifecycleManager;
import com.google.apphosting.api.ApiProxy;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code VmHealthCheckServlet} responds with "ok" to each request. If the app
 * version expected by the VM Runtime is not equal to the current app version,
 * then "version mismatch 'a' != 'b' is returned.
 *
 * This handler should be mapped to /_ah/health on VM Runtimes so health
 * checks can detect when the app is up.
 *
 */
public class VmHealthServlet extends HttpServlet {

  private String fullVersionId() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    String actualVersionId = environment.getVersionId();
    if (!(environment.getModuleId() == null
        || environment.getModuleId().isEmpty()
        || environment.getModuleId().equals("default"))) {
      actualVersionId = environment.getModuleId() + ":" + actualVersionId;
    }
    return actualVersionId;
  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Check if the instance is shutting down, in that case return unhealthy (lameduck).
    LifecycleManager lifeCycleManager = LifecycleManager.getInstance();
    if (LifecycleManager.getInstance().isShuttingDown()) {
      long remainingShutdownTime = lifeCycleManager.getRemainingShutdownTime();
      response.sendError(
          HttpServletResponse.SC_BAD_GATEWAY,
          "App is shutting down, time remaining: " + remainingShutdownTime + " ms");
      return;
    }
    String expectedVersion = request.getParameter("VersionID");
    String actualVersion = fullVersionId();
    if ((expectedVersion == null) || expectedVersion.equals(actualVersion)) {
      response.setContentType("text/plain");
      response.getWriter().write("ok");
    } else {
      response.setContentType("text/plain");
      response
          .getWriter()
          .write(
              String.format("version mismatch \"%s\" != \"%s\"", expectedVersion, actualVersion));
    }
  }
}

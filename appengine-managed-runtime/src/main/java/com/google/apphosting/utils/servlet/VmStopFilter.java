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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Runs the user-registered shutdown hook, if any.
 *
 * This filter should be mapped to /_ah/stop on VM Runtimes so stop requests
 * are properly handled.
 *
 */
public class VmStopFilter implements Filter {

  private static final Logger logger = Logger.getLogger(VmStopFilter.class.getName());

  private static final long SHUTDOWN_HOOK_DEADLINE_MILLIS =
      TimeUnit.SECONDS.convert(60, TimeUnit.MILLISECONDS);

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  /**
   * Handle stop requests.
   *
   * Stop requests are intercepted by this filter and never forwarded to user code.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Requests to /_ah/stop are filtered by the appserver (and soon VM-local nginx). Any stop
    // requests that make it here are from trusted code.
    logger.info("Running shutdown hook");
    long deadline = System.currentTimeMillis() + SHUTDOWN_HOOK_DEADLINE_MILLIS;
    LifecycleManager.getInstance().beginShutdown(deadline);

    response.setContentType("text/plain");
    response.getWriter().write("ok");
  }
}

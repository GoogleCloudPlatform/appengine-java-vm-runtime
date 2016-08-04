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

import com.google.appengine.api.LifecycleManager;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShutdownServlet extends HttpServlet {
  private static final Logger log = Logger.getLogger(ShutdownServlet.class.getName());

  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    if ("false".equals(req.getParameter("hang"))) {
      doHello(res);
    } else {
      doHang(res);
    }
  }

  private void doHang(HttpServletResponse res) throws IOException {
    res.setContentType("text/plain");
    res.getWriter().println("Starting...");
    final Thread requestThread = Thread.currentThread();
    final boolean[] hookState = new boolean[] {false, false};
    LifecycleManager.getInstance()
        .setShutdownHook(
            new LifecycleManager.ShutdownHook() {
              public void shutdown() {
                hookState[0] = true; // Hook called
                LifecycleManager.getInstance().interruptAllRequests();
                hookState[1] = true;
              }
            });
    hang();
    res.getWriter().println("Hook: " + hookState[0] + " " + hookState[1]);
    res.getWriter().println("Exiting.");
  }

  public void hang() {
    LifecycleManager runtime = LifecycleManager.getInstance();
    log.info("Shutting down: " + runtime.isShuttingDown());
    while (!runtime.isShuttingDown()) {
      try {
        log.info("Sleeping for 10s");
        Thread.sleep(10000);
      } catch (InterruptedException ex) {
        // expected
      }
      log.info("Shutting down: " + runtime.isShuttingDown());
    }
  }

  private void doHello(HttpServletResponse res) throws IOException {
    log.info("Hello!");
    LifecycleManager.getInstance()
        .setShutdownHook(
            new LifecycleManager.ShutdownHook() {
              public void shutdown() {
                log.info("Goodbye, world!");
                LifecycleManager.getInstance().interruptAllRequests();
                if (Thread.interrupted()) {
                  log.info("Interrupted");
                }
              }
            });
    res.getWriter().println("Hello, world!");
  }
}

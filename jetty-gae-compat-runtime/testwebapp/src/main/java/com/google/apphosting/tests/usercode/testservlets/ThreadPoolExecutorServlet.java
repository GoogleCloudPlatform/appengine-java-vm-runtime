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

import com.google.appengine.api.ThreadManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet used to test the ThreadPoolExecutor in the JDK.
 */
public class ThreadPoolExecutorServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(ThreadPoolExecutorServlet.class.getName());

  private volatile boolean threadRan = false;

  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    final PrintWriter writer = res.getWriter();
    try {
      final String sleepTime = req.getParameter("sleep");
      final int sleepMillis = Integer.valueOf(sleepTime);

      Callable<Void> callable =
          new Callable<Void>() {
            public Void call() {
              try {
                Thread.sleep(Integer.valueOf(sleepMillis));
              } catch (InterruptedException ex) {
                logger.warning("thread interrupted");
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
              }
              logger.info("thread ran");
              threadRan = true;
              return null;
            }
          };

      ThreadFactory factory = ThreadManager.currentRequestThreadFactory();
      ExecutorService service = Executors.newCachedThreadPool(factory);
      // The timeout is shorter than the sleep in the Callable, so the executor will
      // always try to cancel the running FutureTask.
      List<Future<Void>> futures =
          service.invokeAll(
              Collections.singletonList(callable), sleepMillis / 2, TimeUnit.MILLISECONDS);
      Future<Void> future = futures.get(0);
      if (!future.isCancelled()) {
        throw new RuntimeException("future task was not cancelled");
      }
      service.shutdown();

      res.setContentType("text/plain");
      writer.println(threadRan ? "thread completed" : "thread did not complete");
    } catch (Throwable ex) {
      logger.log(Level.WARNING, "ThreadPoolExecutorServlet threw an exception:", ex);
      throw new ServletException(ex);
    }
  }
}

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
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DeadlineExceededException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ThreadServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(ThreadServlet.class.getName());

  private volatile boolean threadRan = false;

  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    final PrintWriter writer = res.getWriter();
    try {
      final String sleepTime = req.getParameter("sleep");
      final boolean doNotJoin = req.getParameter("no-join") != null;
      final String throwMessage = req.getParameter("throw");
      final boolean uninterruptable = req.getParameter("uninterruptable") != null;
      final boolean joinRepeatedly = req.getParameter("join-repeatedly") != null;
      final boolean testSecurity = req.getParameter("test-security") != null;
      final boolean deadlock = req.getParameter("deadlock") != null;
      final boolean useThreadPool = req.getParameter("thread-pool") != null;
      final boolean shutdownExecutor = req.getParameter("shutdown-thread-pool") != null;
      final boolean background = req.getParameter("background") != null;
      final boolean interrupt = req.getParameter("interrupt") != null;
      final boolean getContextClassLoader = req.getParameter("get-context-class-loader") != null;

      if (deadlock) {
        doDeadlock();
        return;
      }

      Runnable runnable =
          new Runnable() {
            public void run() {
              if (testSecurity) {
                testSecurity();
              }
              if (getContextClassLoader) {
                if (Thread.currentThread().getContextClassLoader()
                    != this.getClass().getClassLoader()) {
                  throw new RuntimeException("found an unexpected context class loader");
                }
              }
              if (ApiProxy.getCurrentEnvironment() == null) {
                throw new RuntimeException("ApiProxy has no environment for this thread.");
              }
              doSleep();
            }

            private void doSleep() {
              if (throwMessage != null) {
                logger.warning("throwing " + throwMessage);
                throw new RuntimeException(throwMessage);
              }
              if (sleepTime != null) {
                if (uninterruptable) {
                  long until = System.currentTimeMillis() + Integer.valueOf(sleepTime);
                  while (true) {
                    try {
                      Thread.sleep(until - System.currentTimeMillis());
                    } catch (InterruptedException ex) {
                      logger.warning("thread interrupted");
                    }
                  }
                } else {
                  try {
                    Thread.sleep(Integer.valueOf(sleepTime));
                  } catch (InterruptedException ex) {
                    logger.warning("thread interrupted");
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                  }
                }
              }

              logger.info("thread ran");
              threadRan = true;
            }

            private void testSecurity() {
              try {
                System.exit(1);
              } catch (SecurityException ex) {
                // expected
              }
            }
          };
      ThreadFactory factory;
      if (background) {
        factory = ThreadManager.backgroundThreadFactory();
      } else {
        factory = ThreadManager.currentRequestThreadFactory();
      }
      if (useThreadPool) {
        ExecutorService service = Executors.newCachedThreadPool(factory);
        Future<?> future = service.submit(runnable);
        if (!doNotJoin) {
          if (joinRepeatedly) {
            while (true) {
              try {
                future.get();
              } catch (InterruptedException | DeadlineExceededException ex) {
                // ignore
              } catch (ExecutionException ex) {
                logger.warning("thread threw: " + ex.getCause());
                break;
              }
            }
          } else {
            try {
              future.get();
            } catch (InterruptedException ex) {
              // ignore
            } catch (ExecutionException ex) {
              logger.warning("thread threw: " + ex.getCause());
            }
          }
        }
        if (shutdownExecutor) {
          service.shutdown();
        }
      } else {
        Thread thread = factory.newThread(runnable);
        thread.setUncaughtExceptionHandler(
            new Thread.UncaughtExceptionHandler() {
              @Override
              public void uncaughtException(Thread t, Throwable e) {
                logger.warning("thread threw: " + e);
              }
            });
        thread.start();
        if (interrupt) {
          thread.interrupt();
        }
        if (!doNotJoin) {
          if (joinRepeatedly) {
            while (true) {
              try {
                thread.join();
              } catch (Throwable th) {
                // ignore
              }
            }
          } else {
            thread.join();
          }
        }
      }

      res.setContentType("text/plain");
      writer.println(threadRan ? "thread completed" : "thread did not complete");
    } catch (Throwable ex) {
      logger.log(Level.WARNING, "ThreadServlet threw an exception:", ex);
      throw new ServletException(ex);
    }
  }

  public void doDeadlock() throws InterruptedException, BrokenBarrierException {
    final Object lock1 = new Object();
    final Object lock2 = new Object();
    final CyclicBarrier barrier = new CyclicBarrier(3);

    Runnable runnable =
        new Runnable() {
          public void run() {
            synchronized (lock1) {
              try {
                barrier.await();
              } catch (Exception ex) {
                ex.printStackTrace();
              }
              synchronized (lock2) {
                lock2.toString();
              }
            }
          }
        };
    ThreadManager.createThreadForCurrentRequest(runnable).start();

    runnable =
        new Runnable() {
          public void run() {
            synchronized (lock2) {
              try {
                barrier.await();
              } catch (Exception ex) {
                ex.printStackTrace();
              }
              synchronized (lock1) {
                lock1.toString();
              }
            }
          }
        };
    ThreadManager.createThreadForCurrentRequest(runnable).start();
    barrier.await();
  }
}

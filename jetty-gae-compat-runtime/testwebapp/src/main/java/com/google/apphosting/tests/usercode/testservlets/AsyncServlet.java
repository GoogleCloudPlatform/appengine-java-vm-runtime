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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AsyncServlet extends HttpServlet {
  private enum Env {
    CONSTRUCT,
    INIT,
    REQUEST,
    ASYNC,
    TIMEOUT,
    ON_DATA_AVAILABLE,
    ON_ALL_DATA_READ,
    STARTED,
    ON_WRITE_POSSIBLE
  }

  private Queue<Env> order = new ConcurrentLinkedQueue<>();
  private ConcurrentMap<Env, Environment> environment = new ConcurrentHashMap<>();

  public AsyncServlet() {
    environment.put(Env.CONSTRUCT, ApiProxy.getCurrentEnvironment());
  }

  @Override
  public void init(ServletConfig servletConfig) throws ServletException {
    environment.put(Env.INIT, ApiProxy.getCurrentEnvironment());
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    if (req.getDispatcherType() == DispatcherType.REQUEST) {
      // #1A POST request dispatched
      order.clear();
      order.add(Env.CONSTRUCT);
      order.add(Env.INIT);
      order.add(Env.REQUEST);
      environment.put(Env.REQUEST, ApiProxy.getCurrentEnvironment());

      final AsyncContext async = req.startAsync();
      final ServletInputStream in = req.getInputStream();
      in.setReadListener(
          new ReadListener() {

            @Override
            public void onError(Throwable t) {
              t.printStackTrace();
            }

            @Override
            public void onDataAvailable() throws IOException {
              // #2A Read data available
              if (!order.contains(Env.ON_DATA_AVAILABLE)) {
                order.add(Env.ON_DATA_AVAILABLE);
              }
              environment.put(Env.ON_DATA_AVAILABLE, ApiProxy.getCurrentEnvironment());
              while (in.isReady()) {
                if (in.read() < 0) {
                  break;
                }
              }
            }

            @Override
            public void onAllDataRead() throws IOException {
              // #3B all data read
              order.add(Env.ON_ALL_DATA_READ);
              environment.put(Env.ON_ALL_DATA_READ, ApiProxy.getCurrentEnvironment());
              async.dispatch(); // Dispatch back to servlet
            }
          });
    } else {
      doGet(req, resp);
    }
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
      throws ServletException, IOException {

    final AsyncContext async = req.startAsync();

    if (req.getDispatcherType() == DispatcherType.REQUEST) {
      // #1A GET Request Dispatch
      order.clear();
      order.add(Env.CONSTRUCT);
      order.add(Env.INIT);
      order.add(Env.REQUEST);
      environment.put(Env.REQUEST, ApiProxy.getCurrentEnvironment());
      async.setTimeout(100);
      async.addListener(
          new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
              // #2B #3B Timeout
              order.add(Env.TIMEOUT);
              environment.put(Env.TIMEOUT, ApiProxy.getCurrentEnvironment());
              async.dispatch(); // Dispatch back to servlet
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {}

            @Override
            public void onError(AsyncEvent event) throws IOException {
              event.getThrowable().printStackTrace();
            }

            @Override
            public void onComplete(AsyncEvent event) throws IOException {}
          });
    } else if (req.getDispatcherType() == DispatcherType.ASYNC) {
      // #4 ASYNC dispatch to servlet
      order.add(Env.ASYNC);
      environment.put(Env.ASYNC, ApiProxy.getCurrentEnvironment());
      async.start(
          new Runnable() {
            @Override
            public void run() {
              try {
                // #5 run started Thread
                order.add(Env.STARTED);
                environment.put(Env.STARTED, ApiProxy.getCurrentEnvironment());
                final ServletOutputStream out = async.getResponse().getOutputStream();
                out.setWriteListener(
                    new WriteListener() {

                      @Override
                      public void onWritePossible() throws IOException {

                        while (out.isReady()) {
                          if (order.contains(Env.ON_WRITE_POSSIBLE)) {
                            async.complete();
                            return;
                          }

                          // #6 on Write Possible
                          order.add(Env.ON_WRITE_POSSIBLE);
                          environment.put(Env.ON_WRITE_POSSIBLE, ApiProxy.getCurrentEnvironment());
                          StringBuilder builder = new StringBuilder();
                          for (Env e : order) {
                            Environment env = environment.get(e);
                            if (builder.length() > 0) {
                              builder.append(',');
                            }
                            builder.append(e).append(':').append(env);
                          }
                          resp.setContentType("text/plain");
                          resp.setStatus(200);
                          out.write(builder.toString().getBytes(StandardCharsets.ISO_8859_1));
                        }
                      }

                      @Override
                      public void onError(Throwable t) {
                        t.printStackTrace();
                      }
                    });

              } catch (Exception t) {
                t.printStackTrace();
              }
            }
          });
    }
  }
}

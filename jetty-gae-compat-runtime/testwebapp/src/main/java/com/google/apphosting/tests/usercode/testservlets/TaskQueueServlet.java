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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * All doXXX methods are synchronized to prevent lastRequestData from
 * getting reset while we're processing.
 *
 */
public class TaskQueueServlet extends HttpServlet {
  // Keep this in sync with X_APPENGINE_DEFAULT_NAMESPACE in
  // http_proto.cc and
  // com.google.appengine.tools.development.LocalHttpRequestEnvironment.DEFAULT_NAMESPACE_HEADER
  // com.google.appengine.api.NamespaceManager.DEFAULT_API_NAMESPACE_KEY
  /**
   * The name of the HTTP header specifying the default namespace
   * for API calls.
   */
  // (Not private so that tests can use it.)
  static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";
  static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";
  static final String CURRENT_NAMESPACE_KEY =
      "com.google.appengine.api.NamespaceManager.currentNamespace";

  private static volatile LastRequestData lastRequestData;

  private static CountDownLatch latch;

  @Override
  protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    if (req.getParameter("getLastPost") != null) {
      printLastRequest(resp);
    } else if (req.getParameter("resetLastRequest") != null) {
      lastRequestData = null;
    } else if (req.getParameter("addTask") != null) {
      addTask(req, resp);
    } else if (req.getParameter("addTasks") != null) {
      addTasks(req);
    } else if (req.getParameter("purgeQueue") != null) {
      purgeQueue(req);
    } else if (req.getParameter("deferredTask") != null) {
      deferredTask(req, resp);
    } else if (req.getParameter("addPullTasks") != null) {
      addPullTasks(req, resp);
    } else if (req.getParameter("leaseTasks") != null) {
      leaseAndDeleteTasks(req, resp);
    } else {
      handleTaskRequest(req);
    }
  }

  private static void gotCalledBack(String data) {
    try {
      lastRequestData = new LastRequestData(DeferredTaskContext.getCurrentRequest(), data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    latch.countDown();
  }

  private static void deferredTask(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String queueName = req.getParameter("queue");
    Queue queue;
    if (queueName == null) {
      queue = QueueFactory.getDefaultQueue();
    } else {
      queue = QueueFactory.getQueue(queueName);
    }
    final String data = req.getParameter("deferredData");

    TaskOptions opts =
        TaskOptions.Builder.withPayload(
            new DeferredTask() {
              @Override
              public void run() {
                gotCalledBack(data);
              }
            });

    latch = new CountDownLatch(1);
    TaskHandle handle = queue.add(opts);
    resp.getWriter().print(handle.getQueueName());
  }

  private void handleTaskRequest(HttpServletRequest req) throws IOException {
    lastRequestData = new LastRequestData(req, null);
    latch.countDown();
  }

  private void purgeQueue(HttpServletRequest req) throws ServletException {
    String queueName = req.getParameter("queue");
    Queue queue;
    if (queueName == null) {
      queue = QueueFactory.getDefaultQueue();
    } else {
      queue = QueueFactory.getQueue(queueName);
    }
    queue.purge();
  }

  private void addTask(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String queueName = req.getParameter("queue");
    Queue queue;
    if (queueName == null) {
      queue = QueueFactory.getDefaultQueue();
    } else {
      queue = QueueFactory.getQueue(queueName);
    }
    TaskOptions.Method method = TaskOptions.Method.valueOf(req.getParameter("httpmethod"));
    String url = req.getParameter("taskUrl");
    if (url == null) {
      url = req.getServletPath();
    }
    TaskOptions opts =
        TaskOptions.Builder.withUrl(url).header("myheader", "blarg29").method(method);

    if (method == TaskOptions.Method.POST || method == TaskOptions.Method.PUT) {
      opts.payload("this=that");
    } else {
      opts.param("myparam", "yam28");
    }
    String execTimeMs = req.getParameter("execTimeMs");
    if (execTimeMs != null) {
      opts.etaMillis(Long.valueOf(execTimeMs));
    }
    String requestNamespace = req.getParameter("requestNamespace");
    if (requestNamespace != null) {
      /* We could override the current environment and set the request namespace
       * but that's a little overkill and already tested in
       * com.google.appengine.api.taskqueue.TaskQueueTest .
       */
      opts.header(DEFAULT_NAMESPACE_HEADER, requestNamespace);
    }
    String currentNamespace = req.getParameter("currentNamespace");
    if (currentNamespace != null) {
      /* We could also do this:
       * opts.header(CURRENT_NAMESPACE_HEADER, currentNamespace);
       */
      NamespaceManager.set(currentNamespace);
    }
    latch = new CountDownLatch(1);
    TaskHandle handle = queue.add(opts);
    resp.getWriter().print(handle.getQueueName());
  }

  private void addTasks(HttpServletRequest req) throws ServletException {
    int numTasks = Integer.parseInt(req.getParameter("numTasks"));
    Queue queue = QueueFactory.getDefaultQueue();

    latch = new CountDownLatch(numTasks);
    queue.add(Collections.nCopies(numTasks, TaskOptions.Builder.withUrl(req.getServletPath())));
  }

  private void addPullTasks(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    int numTasks = Integer.parseInt(req.getParameter("numTasks"));
    String queueName = req.getParameter("queue");
    Queue queue = QueueFactory.getQueue(queueName);

    List<TaskOptions> options = new ArrayList<TaskOptions>();
    for (int i = 0; i < numTasks; i++) {
      options.add(
          TaskOptions.Builder.withMethod(TaskOptions.Method.PULL)
              .payload(String.format("payload-%03d", i).getBytes()));
    }
    List<TaskHandle> tasks = queue.add(options);
    resp.getWriter().print(queueName + "," + tasks.size());
  }

  private void leaseAndDeleteTasks(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    int numTasks = Integer.parseInt(req.getParameter("numTasks"));
    Double lease = Double.parseDouble(req.getParameter("lease"));
    String queueName = req.getParameter("queue");
    Queue queue = QueueFactory.getQueue(queueName);
    Boolean doDelete = Boolean.parseBoolean(req.getParameter("doDelete"));

    List<TaskHandle> tasks =
        queue.leaseTasks(lease.intValue() * 1000, TimeUnit.MILLISECONDS, numTasks);

    for (TaskHandle task : tasks) {
      if (doDelete) {
        queue.deleteTask(task.getName());
      }
    }
    resp.getWriter().print(queueName + "," + tasks.size());
  }

  @Override
  protected synchronized void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    handleTaskRequest(req);
  }

  @Override
  protected synchronized void doHead(
      HttpServletRequest req, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    handleTaskRequest(req);
  }

  @Override
  protected synchronized void doPut(HttpServletRequest req, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    handleTaskRequest(req);
  }

  @Override
  protected synchronized void doDelete(
      HttpServletRequest req, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    handleTaskRequest(req);
  }

  private static final String SEPARATOR = "_____";

  private static void printLastRequest(HttpServletResponse resp) throws IOException {
    if (lastRequestData == null) {
      resp.setStatus(503);
      return;
    }
    PrintWriter pw = resp.getWriter();
    pw.println(lastRequestData.method);
    pw.println(toString(lastRequestData.headerMap));
    pw.println(toString(lastRequestData.paramMap));
    pw.println(lastRequestData.payload);
    if (!lastRequestData.requestNamespace.equals("")) {
      pw.println("requestNamespace:" + lastRequestData.requestNamespace);
    } else {
      pw.println("requestNamespace");
    }
    if (lastRequestData.currentNamespace != null) {
      pw.println("currentNamespace:" + lastRequestData.currentNamespace);
    } else {
      pw.println("currentNamespace");
    }
    if (lastRequestData.deferredData != null) {
      pw.println("deferredData:" + lastRequestData.deferredData);
    } else {
      pw.println("deferredData");
    }
  }

  private static final class LastRequestData {
    private final String method;
    private final Map<String, String> headerMap;
    private final Map<String, String> paramMap;
    private final String payload;
    private final String requestNamespace;
    private final String currentNamespace;
    private final String deferredData;

    private LastRequestData(HttpServletRequest req, String deferredData) throws IOException {
      Environment environment = ApiProxy.getCurrentEnvironment();
      requestNamespace = NamespaceManager.getGoogleAppsNamespace();
      currentNamespace = NamespaceManager.get();

      method = req.getMethod();
      headerMap = new HashMap<String, String>();
      for (String key : asIterable((Enumeration<String>) req.getHeaderNames())) {
        headerMap.put(key, req.getHeader(key));
      }
      paramMap = new HashMap<String, String>();
      for (String key : asIterable((Enumeration<String>) req.getParameterNames())) {
        paramMap.put(key, req.getParameter(key));
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      if (sb.length() == 0) {
        payload = " ";
      } else {
        payload = sb.toString();
      }
      this.deferredData = deferredData;
    }
  }

  private static String toString(Map<String, String> map) {
    StringBuilder sb = new StringBuilder();
    if (map.isEmpty()) {
      return " ";
    }
    for (Map.Entry<String, String> entry : map.entrySet()) {
      sb.append(entry.getKey()).append(SEPARATOR).append(entry.getValue()).append(SEPARATOR);
    }
    return sb.toString();
  }

  private static <T> Iterable<T> asIterable(Enumeration<T> e) {
    final List<T> list = new ArrayList<T>();
    for (; e.hasMoreElements(); ) {
      list.add(e.nextElement());
    }
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        return list.iterator();
      }
    };
  }

  public static CountDownLatch getLatch() {
    return latch;
  }
}

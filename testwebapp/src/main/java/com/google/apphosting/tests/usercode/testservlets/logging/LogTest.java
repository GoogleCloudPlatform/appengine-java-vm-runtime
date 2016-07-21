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

package com.google.apphosting.tests.usercode.testservlets.logging;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests java.util.logging configuration from a file.
 *
 */
public class LogTest extends HttpServlet {

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    getServletContext().log("LogTest Hello");
    getServletContext().log("LogTest Exception ", new Throwable());

    String configFile = System.getProperty("java.util.logging.config.file");

    if (configFile == null) {
      throw new ServletException("Expected a config file to be set.");
    }

    // TODO(tobyr) Add some different properties files to test
    if (configFile.endsWith("logging.properties")) {
      test1();
    } else {
      throw new ServletException("Unexpected config file: " + configFile);
    }
  }

  private void test1() {
    assertEquals(Level.ALL, Logger.getLogger(""));

    if (!testConfigRan) {
      throw new RuntimeException("Expected TestConfig to have executed.");
    }

    assertEquals(Level.INFO, Logger.getLogger("com.foo"));
    assertEquals(Level.SEVERE, Logger.getLogger("com.foo.bar"));
    assertEquals(null, Logger.getLogger("com"));
    assertEquals(null, Logger.getLogger("com.foo.baz"));

    LogRecorder recorder = new LogRecorder();

    try {
      Logger comLogger = Logger.getLogger("com");
      comLogger.fine("Hello");
      assertEquals(1, recorder.records.size());
      comLogger.warning("Warning");
      assertEquals(2, recorder.records.size());

      recorder.records.clear();

      Logger bazLogger = Logger.getLogger("com.foo.baz");
      bazLogger.fine("Hello");
      assertEquals(0, recorder.records.size());
      comLogger.info("Info");
      assertEquals(1, recorder.records.size());
    } finally {
      recorder.reset();
    }
  }

  private static void assertEquals(Level expected, Logger actual) {
    if (expected != actual.getLevel()) {
      throw new RuntimeException("Expected " + expected + ", actual " + actual.getLevel());
    }
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new RuntimeException("Expected " + expected + ", actual " + actual);
    }
  }

  public static boolean testConfigRan;

  public static class TestConfig {
    public TestConfig() {
      testConfigRan = true;
    }
  }

  private static class LogRecorder implements ApiProxy.Delegate {
    private ApiProxy.Delegate oldDelegate;
    private List<LogRecord> records = new ArrayList<LogRecord>();

    public LogRecorder() {
      oldDelegate = ApiProxy.getDelegate();
      ApiProxy.setDelegate(this);
    }

    public List<Thread> getRequestThreads(Environment environmnent) {
      return oldDelegate.getRequestThreads(environmnent);
    }

    public byte[] makeSyncCall(
        Environment environment, String packageName, String methodName, byte[] request)
        throws ApiProxyException {
      return oldDelegate.makeSyncCall(environment, packageName, methodName, request);
    }

    public Future<byte[]> makeAsyncCall(
        Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      return oldDelegate.makeAsyncCall(environment, packageName, methodName, request, apiConfig);
    }

    public void log(Environment environment, LogRecord record) {
      records.add(record);
      oldDelegate.log(environment, record);
    }

    public void flushLogs(Environment environment) {
      oldDelegate.flushLogs(environment);
    }

    public void reset() {
      ApiProxy.setDelegate(oldDelegate);
    }
  }
}

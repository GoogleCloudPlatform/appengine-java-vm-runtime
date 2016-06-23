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

package com.google.apphosting.logging;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class JsonFormatterTest {

  private JsonFormatter formatter = new JsonFormatter();

  private JsonCaptureHandler capturingHandler;

  @Before
  public void initLoggingCapture() {
    Logger root = Logger.getLogger("");
    root.setLevel(Level.FINE);

    capturingHandler = new JsonCaptureHandler();
    root.addHandler(capturingHandler);
  }

  @Test
  public void formatProducesExpectedJsonData() throws Exception {
    LogRecord record = new LogRecord(Level.INFO, "message");
    record.setMillis(12345_678);
    record.setLoggerName("logger");
    LogContext.current().put("traceId", "abcdef");

    String logLine = formatter.format(record);
    assertThat(logLine, endsWith(System.lineSeparator()));

    JsonData data = new Gson().fromJson(logLine, JsonData.class);
    assertEquals("INFO", data.severity);
    assertEquals(12345, data.timestamp.seconds);
    assertEquals(678_000_000, data.timestamp.nanos);
    assertEquals(Thread.currentThread().getName(), data.thread);
    assertEquals("logger: message", data.message);
    assertEquals("abcdef", data.traceId);
  }

  @Test
  public void messageIncludesLoggerName() throws Exception {
    LogRecord record = new LogRecord(Level.INFO, "message");
    record.setLoggerName("logger");
    assertEquals("logger: message", formatter.formatMessage(record));
  }

  @Test
  public void messageIncludesClassName() throws Exception {
    LogRecord record = new LogRecord(Level.INFO, "message");
    record.setSourceClassName("class");
    record.setLoggerName("logger");
    assertEquals("class: message", formatter.formatMessage(record));
  }

  @Test
  public void messageIncludesMethodName() throws Exception {
    LogRecord record = new LogRecord(Level.INFO, "message");
    record.setSourceClassName("class");
    record.setSourceMethodName("method");
    assertEquals("class method: message", formatter.formatMessage(record));
  }

  @Test
  public void messageIncludesStackTrace() throws Exception {
    LogRecord record = new LogRecord(Level.INFO, "message");
    record.setLoggerName("logger");
    record.setThrown(new Throwable("thrown").fillInStackTrace());
    String message = formatter.formatMessage(record);
    BufferedReader reader = new BufferedReader(new StringReader(message));
    assertEquals("logger: message", reader.readLine());
    assertEquals("java.lang.Throwable: thrown", reader.readLine());
    assertTrue(reader.readLine()
        .startsWith("\tat " + getClass().getName() + ".messageIncludesStackTrace"));
  }

  @Test
  public void testCommonsLogging() {
    final Instant now = Instant.now();
    final String expectedThreadName = Thread.currentThread().getName();

    // Generate Events from Commons Logging
    new CommonsLoggingExample().run();

    // Verify that event was captured by slf4j -> java.util.logging
    String[][] expected = new String[][] {{"DEBUG", "A CommonsLogging Debug Event: "},
        {"INFO", "A CommonsLogging Info Event: "}, {"WARNING", "A CommonsLogging Warn Event: "},
        {"ERROR", "A CommonsLogging Error Event: "}};

    List<String> events = capturingHandler.getEvents();
    assertThat("Events", events.size(), is(expected.length));

    for (int i = 0; i < events.size(); i++) {
      String logLine = events.get(i);
      System.out.printf("logLine[%d] = %s", i, logLine);
      JsonData data = new Gson().fromJson(logLine, JsonData.class);
      assertThat("severity", data.severity, is(expected[i][0]));
      assertThat("timestamp.seconds", data.timestamp.seconds,
          greaterThanOrEqualTo(now.getEpochSecond()));
      assertThat("timestamp.nanos", data.timestamp.nanos, greaterThanOrEqualTo(now.getNano()));
      assertThat("thread name", data.thread, is(expectedThreadName));
      assertThat("logger message", data.message, startsWith(CommonsLoggingExample.class.getName() + " run: " + expected[i][1]));
      if (data.severity.equals("ERROR")) {
        assertThat("throwable", data.message,
            containsString("java.lang.RuntimeException: Generic Error"));
      }
    }
  }

  @Test
  public void testJavaUtilLogging() {
    final Instant now = Instant.now();
    final String expectedThreadName = Thread.currentThread().getName();

    // Generate Events from java.util.logging
    new JulExample().run();

    // Verify that event was captured by slf4j -> java.util.logging
    String[][] expected =
        new String[][] {{"DEBUG", "A JUL Fine Event: "}, {"DEBUG", "A JUL Config Event: "},
            {"INFO", "A JUL Info Event: "}, {"WARNING", "A JUL Warning Event: "},
            {"ERROR", "A JUL Severe Event: "}};

    List<String> events = capturingHandler.getEvents();
    assertThat("Events", events.size(), is(expected.length));

    for (int i = 0; i < events.size(); i++) {
      String logLine = events.get(i);
      System.out.printf("logLine[%d] = %s", i, logLine);
      JsonData data = new Gson().fromJson(logLine, JsonData.class);
      assertThat("severity", data.severity, is(expected[i][0]));
      assertThat("timestamp.seconds", data.timestamp.seconds,
          greaterThanOrEqualTo(now.getEpochSecond()));
      assertThat("timestamp.nanos", data.timestamp.nanos, greaterThanOrEqualTo(now.getNano()));
      assertThat("thread name", data.thread, is(expectedThreadName));
      assertThat("logger message", data.message, startsWith(JulExample.class.getName() + " run: " + expected[i][1]));
      if (data.severity.equals("ERROR")) {
        assertThat("throwable", data.message,
            containsString("java.lang.RuntimeException: Generic Error"));
      }
    }
  }

  @Test
  public void testLog4jLogging() {
    final Instant now = Instant.now();
    final String expectedThreadName = Thread.currentThread().getName();

    // Generate Events from Apache Log4j Logging
    new Log4jExample().run();

    // Verify that event was captured by slf4j -> java.util.logging
    String[][] expected =
        new String[][] {{"DEBUG", "A Log4j Debug Event: "}, {"INFO", "A Log4j Info Event: "},
            {"WARNING", "A Log4j Warn Event: "}, {"ERROR", "A Log4j Error Event: "}};

    List<String> events = capturingHandler.getEvents();
    assertThat("Events", events.size(), is(expected.length));

    for (int i = 0; i < events.size(); i++) {
      String logLine = events.get(i);
      System.out.printf("logLine[%d] = %s", i, logLine);
      JsonData data = new Gson().fromJson(logLine, JsonData.class);
      assertThat("severity", data.severity, is(expected[i][0]));
      assertThat("timestamp.seconds", data.timestamp.seconds,
          greaterThanOrEqualTo(now.getEpochSecond()));
      assertThat("timestamp.nanos", data.timestamp.nanos, greaterThanOrEqualTo(now.getNano()));
      assertThat("thread name", data.thread, is(expectedThreadName));
      assertThat("logger message", data.message, startsWith(Log4jExample.class.getName() + " run: " + expected[i][1]));
      if (data.severity.equals("ERROR")) {
        assertThat("throwable", data.message,
            containsString("java.lang.RuntimeException: Generic Error"));
      }
    }
  }

  @Test
  public void testSlf4jLogging() {
    final Instant now = Instant.now();
    final String expectedThreadName = Thread.currentThread().getName();

    // Generate Events from Slf4j Logging
    new Slf4jExample().run();

    // Verify that event was captured by slf4j -> java.util.logging
    String[][] expected =
        new String[][] {{"DEBUG", "A Slf4j Debug Event: "}, {"INFO", "A Slf4j Info Event: "},
            {"WARNING", "A Slf4j Warn Event: "}, {"ERROR", "A Slf4j Error Event: "}};

    List<String> events = capturingHandler.getEvents();
    assertThat("Events", events.size(), is(expected.length));

    for (int i = 0; i < events.size(); i++) {
      String logLine = events.get(i);
      System.out.printf("logLine[%d] = %s", i, logLine);
      JsonData data = new Gson().fromJson(logLine, JsonData.class);
      assertThat("severity", data.severity, is(expected[i][0]));
      assertThat("timestamp.seconds", data.timestamp.seconds,
          greaterThanOrEqualTo(now.getEpochSecond()));
      assertThat("timestamp.nanos", data.timestamp.nanos, greaterThanOrEqualTo(now.getNano()));
      assertThat("thread name", data.thread, is(expectedThreadName));
      assertThat("logger message", data.message, startsWith(Slf4jExample.class.getName() + " run: " + expected[i][1]));
      if (data.severity.equals("ERROR")) {
        assertThat("throwable", data.message,
            containsString("java.lang.RuntimeException: Generic Error"));
      }
    }
  }


  // Something that JSON can parser the JSON into
  public static class JsonData {
    public static class LogTimestamp {
      public long seconds;
      public int nanos;
    }


    public LogTimestamp timestamp;
    public String severity;
    public String thread;
    public String message;
    public String traceId;
  }
}

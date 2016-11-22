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

import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class JsonFormatterTest {

  private JsonFormatter formatter = new JsonFormatter();

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
    assertEquals(Long.toHexString(Thread.currentThread().getId()), data.thread);
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
    assertTrue(
        reader
            .readLine()
            .startsWith("\tat " + getClass().getName() + ".messageIncludesStackTrace"));
  }

  // Something that JSON can parser the JSON into
  public static class JsonData {
    public static class LogTimestamp {
      public long seconds;
      public long nanos;
    }

    public LogTimestamp timestamp;
    public String severity;
    public String thread;
    public String message;
    public String traceId;
  }
}

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

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A Formatter for use with java.util.logging that outputs LogRecords as JSON data with the
 * properties that App Engine expects.
 */
public class JsonFormatter extends Formatter {
  @Override
  public String format(LogRecord record) {
    Instant timestamp = Instant.ofEpochMilli(record.getMillis());
    StringWriter out = new StringWriter();

    // Write using a simple JsonWriter rather than the more sophisticated Gson as we generally
    // will not need to serialize complex objects that require introspection and reflection.
    try (JsonWriter writer = new JsonWriter(out)) {
      writer.setSerializeNulls(false);
      writer.setHtmlSafe(false);

      writer.beginObject();
      writer
          .name("timestamp")
          .beginObject()
          .name("seconds")
          .value(timestamp.getEpochSecond())
          .name("nanos")
          .value(timestamp.getNano())
          .endObject();
      writer.name("severity").value(severity(record.getLevel()));
      writer.name("thread").value(Integer.toHexString(record.getThreadID()));
      writer.name("message").value(formatMessage(record));

      // If there is a LogContext associated with this thread then add its properties.
      LogContext logContext = LogContext.current();
      if (logContext != null) {
        logContext.forEach(
            (name, value) -> {
              try {
                writer.name(name);
                if (value == null) {
                  writer.nullValue();
                } else if (value instanceof Boolean) {
                  writer.value((boolean) value);
                } else if (value instanceof Number) {
                  writer.value((Number) value);
                } else {
                  writer.value(value.toString());
                }
              } catch (IOException e) {
                // Should not happen as StringWriter does not throw IOException
                throw new AssertionError(e);
              }
            });
      }
      writer.endObject();
    } catch (IOException e) {
      // Should not happen as StringWriter does not throw IOException
      throw new AssertionError(e);
    }
    out.append(System.lineSeparator());
    return out.toString();
  }

  @Override
  public synchronized String formatMessage(LogRecord record) {
    StringBuilder sb = new StringBuilder();
    if (record.getSourceClassName() != null) {
      sb.append(record.getSourceClassName());
    } else {
      sb.append(record.getLoggerName());
    }
    if (record.getSourceMethodName() != null) {
      sb.append(' ');
      sb.append(record.getSourceMethodName());
    }
    sb.append(": ");
    sb.append(super.formatMessage(record));
    Throwable thrown = record.getThrown();
    if (thrown != null) {
      StringWriter sw = new StringWriter();
      try (PrintWriter pw = new PrintWriter(sw)) {
        sb.append('\n');
        thrown.printStackTrace(pw);
      }
      sb.append(sw.getBuffer());
    }
    return sb.toString();
  }

  private static String severity(Level level) {
    int intLevel = level.intValue();

    if (intLevel >= Level.SEVERE.intValue()) {
      return "ERROR";
    } else if (intLevel >= Level.WARNING.intValue()) {
      return "WARNING";
    } else if (intLevel >= Level.INFO.intValue()) {
      return "INFO";
    } else {
      // There's no trace, so we'll map everything below this to debug.
      return "DEBUG";
    }
  }
}

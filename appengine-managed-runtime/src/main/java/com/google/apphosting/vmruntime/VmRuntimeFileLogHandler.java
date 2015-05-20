/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.vmruntime;


import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code VmRuntimeFileLogHandler} is installed on the root logger. It converts all messages
 * to the json format understood by the cloud logging agent and logs to a file in a volume shared
 * with the cloud logging agent.
 *
 */
public class VmRuntimeFileLogHandler extends FileHandler {
  // This exists for testing purposes only.  If set, the cloud logger may lose logs.
  private static final String LOG_PATTERN_CONFIG_PROPERTY =
      "com.google.apphosting.vmruntime.VmRuntimeFileLogHandler.pattern";
  // Log files to /var/log/app_engine/app.[0-2].log.json
  private static final String DEFAULT_LOG_PATTERN = "/var/log/app_engine/app.%g.log.json";
  private static final String APP_ENGINE_LOG_CONFIG_PATTERN_ENV =
      "APP_ENGINE_LOG_CONFIG_PATTERN";
  private static final int LOG_MAX_SIZE = 100 * 1024 * 1024;
  private static final int LOG_MAX_FILES = 3;

  private VmRuntimeFileLogHandler() throws IOException {
    super(fileLogPattern(), LOG_MAX_SIZE, LOG_MAX_FILES, true);
    setLevel(Level.FINEST);
    setFormatter(new CustomFormatter());
  }

  private static String fileLogPattern() {
    String pattern = System.getenv(APP_ENGINE_LOG_CONFIG_PATTERN_ENV);
    // For Cloud SDK usage only for local Jetty processes.
    if (pattern != null) {
      return pattern;
    }
    pattern = System.getProperty(LOG_PATTERN_CONFIG_PROPERTY);
    if (pattern != null) {
      return pattern;
    }
    return DEFAULT_LOG_PATTERN;
  }

  /**
   * Initialize the {@code VmRuntimeFileLogHandler} by installing it on the root logger.
   */
  public static void init() throws IOException {
    Logger rootLogger = Logger.getLogger("");
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof VmRuntimeFileLogHandler) {
        return; // Already installed.
      }
    }
    rootLogger.addHandler(new VmRuntimeFileLogHandler());
  }

  /**
   * Convert from a Java Logging level to a cloud logs logging level.
   * SEVERE maps to error, WARNING to warn, INFO to info, and all
   * lower levels to debug.  We reserve the fatal level for exceptions
   * that propagated outside of user code and forced us to kill the
   * request.
   */
  private static String convertLogLevel(Level level) {
    long intLevel = level.intValue();

    if (intLevel >= Level.SEVERE.intValue()) {
      return "ERROR";
    } else if (intLevel >= Level.WARNING.intValue()) {
      return "WARNING";
    } else if (intLevel >= Level.INFO.intValue()) {
      return "INFO";
    } else {
      // There's no trace, so we'll map everything below this to
      // debug.
      return "DEBUG";
    }
  }

  /**
   * Used by LogData to format timestamps.
   */
  
  public static final class LogTimestamp {
    private long seconds;
    private long nanos;

    LogTimestamp(long seconds, long nanos) {
      this.seconds = seconds;
      this.nanos = nanos;
    }

    public long getSeconds() {
      return seconds;
    }

    public long getNanos() {
      return nanos;
    }
  }

  /**
   * Class for logging user data via GSON.
   */
  
  public static final class LogData {
    private String message;
    private LogTimestamp timestamp;
    private String thread;
    private String severity;

    LogData(String message, long seconds, long nanos, String thread, String severity) {
      this.message = message;
      this.timestamp = new LogTimestamp(seconds, nanos);
      this.thread = thread;
      this.severity = severity;
    }

    public String getMessage() {
      return message;
    }

    public LogTimestamp getTimestamp() {
      return timestamp;
    }

    public String getThread() {
      return thread;
    }

    public String getSeverity() {
      return severity;
    }
  }

  private static final class CustomFormatter extends Formatter {

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    @Override
    public synchronized String format(LogRecord record) {
      StringBuffer sb = new StringBuffer();
      if (record.getSourceClassName() != null) {
        sb.append(record.getSourceClassName());
      } else {
        sb.append(record.getLoggerName());
      }
      if (record.getSourceMethodName() != null) {
        sb.append(" ");
        sb.append(record.getSourceMethodName());
      }
      sb.append(": ");
      String message = formatMessage(record);
      sb.append(message);
      if (record.getThrown() != null) {
        try {
          sb.append("\n");
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          record.getThrown().printStackTrace(pw);
          pw.close();
          sb.append(sw.toString());
        } catch (Exception ex) {
          // Ignored.  (Shouldn't happen and if it does we can't do much about it.)
        }
      }
      Gson gson = new Gson();
      message = sb.toString();
      long seconds = record.getMillis() / 1000;
      long nanos = (record.getMillis() % 1000) * 1000000;
      String thread = Integer.toString(record.getThreadID());
      String severity = convertLogLevel(record.getLevel());
      return gson.toJson(new LogData(message, seconds, nanos, thread, severity)) + "\n";
    }
  }
}

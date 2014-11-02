/**
 * Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code VmRuntimeLogHandler} is installed on the root logger. It forwards all messages on to
 * {@code ApiProxy.log(ApiProxy.LogRecord)}, where they can be attached to the runtime response.
 *
 */
public class VmRuntimeLogHandler extends Handler {
  
  static final String JAVA_UTIL_LOGGING_CONFIG_PROPERTY = "java.util.logging.config.file";

  private static final Logger ROOT_LOGGER = Logger.getLogger("");

  private VmRuntimeLogHandler() {
    setLevel(Level.FINEST);
    setFilter(new ApiProxyLogFilter());
    setFormatter(new CustomFormatter());
  }

  /**
   * Reloads logging to pick up changes to the java.util.logging.config.file system property.
   */
  private static void reloadLoggingProperties(LogManager logManager) {
    if (System.getProperty(JAVA_UTIL_LOGGING_CONFIG_PROPERTY) == null) {
      return;
    }
    try {
      logManager.readConfiguration();
    } catch (SecurityException | IOException e) {
      System.err.println("Warning: caught exception when reading logging properties.");
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
  }

  /**
   * Test-only init method to allow unit test to use a custom logManager.
   *
   * @param logManager
   */
  
  static void init(LogManager logManager) {
    reloadLoggingProperties(logManager);
    for (Handler handler : ROOT_LOGGER.getHandlers()) {
      if (handler instanceof VmRuntimeLogHandler) {
        return;
      }
    }
    ROOT_LOGGER.addHandler(new VmRuntimeLogHandler());
  }

  /**
   * Initialize the {@code VmRuntimeLogHandler} by installing it on the root logger. After this call
   * all log messages are forwarded to {@code ApiProxy.log(ApiProxy.LogRecord)} so they can be
   * attached to the runtime response.
   */
  public static void init() {
    init(LogManager.getLogManager());
  }

  @Override
  public void publish(LogRecord record) {
    if (!isLoggable(record)) {
      return;
    }

    String message;
    synchronized (this) {
      try {
        message = getFormatter().format(record);
      } catch (Exception ex) {
        reportError(null, ex, ErrorManager.FORMAT_FAILURE);
        return;
      }
    }

    VmApiProxyEnvironment environment = getThreadLocalEnvironment();
    if (environment != null) {
      environment.addLogRecord(convertLogRecord(record, message));
    }
  }

  private ApiProxy.LogRecord convertLogRecord(LogRecord record, String message) {
    ApiProxy.LogRecord.Level level = convertLogLevel(record.getLevel());
    long timestamp = record.getMillis() * 1000;

    return new ApiProxy.LogRecord(level, timestamp, message);
  }

  @Override
  public void flush() {
    VmApiProxyEnvironment environment = getThreadLocalEnvironment();
    if (environment != null) {
      environment.flushLogs();
    }
  }

  @Override
  public void close() {
    flush();
  }

  private ApiProxy.LogRecord.Level convertLogLevel(Level level) {
    long intLevel = level.intValue();

    if (intLevel >= Level.SEVERE.intValue()) {
      return ApiProxy.LogRecord.Level.error;
    } else if (intLevel >= Level.WARNING.intValue()) {
      return ApiProxy.LogRecord.Level.warn;
    } else if (intLevel >= Level.INFO.intValue()) {
      return ApiProxy.LogRecord.Level.info;
    } else {
      return ApiProxy.LogRecord.Level.debug;
    }
  }

  /**
   * Returns the thread local environment if it is a VmApiProxyEnvironment.
   *
   * @return The ThreadLocal environment or null if no VmApiProxyEnvironment is set on this thread.
   */
  private VmApiProxyEnvironment getThreadLocalEnvironment() {
    Environment env = ApiProxy.getCurrentEnvironment();
    if (env instanceof VmApiProxyEnvironment) {
      return (VmApiProxyEnvironment) env;
    }
    return null;
  }

  /**
   * Filter used to exclude error messages caused by log API calls.
   *
   * <p>Log messages related to the log system have an unfortunate tendency to trigger infinite
   * loops. For example, we must drop messages from @code{VmApiProxyDelegate} if the message is due
   * to logservice. Otherwise if the subsequent log API flush fails it will trigger some logging,
   * which in turn will trigger a flush, which most likely will fail again causing some logging and
   * so on.
   *
   * <p>The following logs are therefore not forwarded to the logging API:
   * <ul>
   *   <li> Logs from {@code VmApiProxyDelegate} related to "logservice".</li>
   *   <li> Any logs from {@code VmAppLogsWriter}.</li>
   * </ul>
   * These logs will only show up in the log file on the VM.
   */
  private static final class ApiProxyLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
      if (VmApiProxyDelegate.class.getName().equals(record.getLoggerName())
          && record.getMessage() != null && record.getMessage().contains("logservice")) {
        return false;
      } else if (VmAppLogsWriter.class.getName().equals(record.getLoggerName())) {
        return false;
      }
      return true;
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
      sb.append("\n");
      if (record.getThrown() != null) {
        try {
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          record.getThrown().printStackTrace(pw);
          pw.close();
          sb.append(sw.toString());
        } catch (Exception ex) {
        }
      }
      return sb.toString();
    }
  }
}

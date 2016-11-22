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

package com.google.apphosting.vmruntime;

import com.google.apphosting.logging.JsonFormatter;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code VmRuntimeFileLogHandler} is installed on the root logger. It converts all messages
 * to the json format understood by the cloud logging agent and logs to a file in a volume shared
 * with the cloud logging agent.
 */
public class VmRuntimeFileLogHandler extends FileHandler {

  // This exists for testing purposes only.  If set, the cloud logger may lose logs.
  public static final String LOG_DIRECTORY_PROPERTY = "com.google.apphosting.logs";
  public static final String LOG_PATTERN_CONFIG_PROPERTY =
      "com.google.apphosting.vmruntime.VmRuntimeFileLogHandler.pattern";
  // Log files to /var/log/app_engine/app.[0-2].log.json
  private static final String DEFAULT_LOG_DIRECTORY = "/var/log/app_engine";
  private static final String DEFAULT_LOG_PATTERN = "app.%g.log.json";
  private static final String APP_ENGINE_LOG_CONFIG_PATTERN_ENV = "APP_ENGINE_LOG_CONFIG_PATTERN";
  private static final int LOG_MAX_SIZE = 100 * 1024 * 1024;
  private static final int LOG_MAX_FILES = 3;
  public static final String JAVA_UTIL_LOGGING_CONFIG_PROPERTY = "java.util.logging.config.file";
  
  private static final LogRecord CLOSE_RECORD = new LogRecord(Level.ALL, "CLOSE");
  private final BlockingQueue<LogRecord> queue = new LinkedBlockingQueue<>();
  
  
  private VmRuntimeFileLogHandler() throws IOException {
    super(fileLogPattern(), LOG_MAX_SIZE, LOG_MAX_FILES, true);
    setLevel(Level.FINEST);
    setFormatter(new JsonFormatter());
    new LoggerThread().start();
  }

  private static String fileLogPattern() {
    String pattern = System.getenv(APP_ENGINE_LOG_CONFIG_PATTERN_ENV);
    // For Cloud SDK usage only for local Jetty processes.
    if (pattern != null) {
      return pattern;
    }

    String directory = System.getProperty(LOG_DIRECTORY_PROPERTY, DEFAULT_LOG_DIRECTORY);

    pattern = System.getProperty(LOG_PATTERN_CONFIG_PROPERTY);
    if (pattern != null) {
      if (pattern.startsWith("/")) {
        return pattern;
      }
      return directory + "/" + pattern;
    }

    return directory + "/" + DEFAULT_LOG_PATTERN;
  }

  /**
   * Initialize the {@code VmRuntimeFileLogHandler} by installing it on the root logger.
   */
  public static void init() throws IOException {
    reloadLoggingProperties(LogManager.getLogManager());
    Logger rootLogger = Logger.getLogger("");
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof VmRuntimeFileLogHandler) {
        return; // Already installed.
      }
    }
    rootLogger.addHandler(new VmRuntimeFileLogHandler());
  }

  /**
   * Reloads logging to pick up changes to the java.util.logging.config.file system property.
   */
  private static void reloadLoggingProperties(LogManager logManager) {
    String logging = System.getProperty(VmRuntimeFileLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY);
    if (logging == null) {
      return;
    }
    try {
      logManager.readConfiguration();
    } catch (SecurityException | IOException e) {
      e.printStackTrace();
      System.err.println("Warning: caught exception when reading logging properties.");
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
  }

  @Override
  public void close() throws SecurityException {
    super.close();
    queue.offer(CLOSE_RECORD);
  }

  @Override
  public void publish(LogRecord record) {
    if (isLoggable(record)) {
      queue.offer(record);
    }
  }

  private void superPublish(LogRecord record) {
    super.publish(record);
  }
  
  class LoggerThread extends Thread {
    @Override
    public void run() {
      while (true) {
        try {
          LogRecord record = queue.poll(1, TimeUnit.HOURS);
          if (record == CLOSE_RECORD) {
            return;
          } else {
            superPublish(record);
          }
        } catch (InterruptedException e) {
          return;
        }
      }
    }
  }
}

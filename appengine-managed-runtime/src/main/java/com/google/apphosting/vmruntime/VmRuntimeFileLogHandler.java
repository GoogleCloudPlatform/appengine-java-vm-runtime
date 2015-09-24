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

import com.google.apphosting.logging.JsonFormatter;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
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
    setFormatter(new JsonFormatter());
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
}

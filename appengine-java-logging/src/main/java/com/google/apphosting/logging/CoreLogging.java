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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.logging.LogManager;

/**
 * Logging Core behavior.
 */
public class CoreLogging {
  public static final String JAVA_UTIL_LOGGING_CONFIG_PROPERTY = "java.util.logging.config.file";

  /**
   * Initialize the java.util.logging Environment.
   * <p>
   * Order is:
   * <ol>
   * <li>Apply App (User) Configuration</li>
   * <li>Apply System Mandated Configuration</li>
   * </ol>
   * </p>
   *
   * @param appConfigFile the filename for the (optional) app specific java.util.logging
   *                      properties file configuration.
   */
  public static void init(File appConfigFile) throws IOException {
    // Use App (User) Configuration specified as a file parameter
    if (appConfigFile != null && appConfigFile.exists()) {
      appConfig(appConfigFile);
    } else {
      // Use App (User) Configuration specified as a System property
      String julConfigFile = System.getProperty(JAVA_UTIL_LOGGING_CONFIG_PROPERTY);
      if (julConfigFile != null) {
        File configFile = new File(julConfigFile);
        if (configFile.exists()) {
          appConfig(configFile);
        }
      }
    }

    // Manually Adjust Configuration to support System Logging Requirements
    systemConfig();
  }

  /**
   * Convenience method for {@link #init(File)}
   *
   * @param appConfigFilename the filename of the config file (or null)
   * @throws IOException if unable to configure the logging
   */
  public static void init(String appConfigFilename) throws IOException {
    File appConfigFile = null;
    if (appConfigFilename == null) {
      appConfigFile = new File(appConfigFilename);
    }
    init(appConfigFile);
  }

  private static void appConfig(File configFile) {
    try (FileInputStream is = new FileInputStream(configFile)) {
      LogManager logManager = LogManager.getLogManager();
      logManager.reset();
      logManager.readConfiguration(is);
    } catch (SecurityException | IOException e) {
      System.err.println("Warning: caught exception when reading logging properties: " + configFile
          .getAbsolutePath());
      e.printStackTrace(System.err);
    }
  }

  /**
   * Manually Configure the System Level requirements for java.util.logging
   */
  private static void systemConfig() throws IOException {
    // Since System Loggers can arrive from deep within the various compat layers, it
    // makes sense to use the ServiceLoader to obtain references to specific System loggers
    // that need to be initialized during this step in the logging initialization.

    ServiceLoader<SystemLogger> serviceLoader = ServiceLoader.load(SystemLogger.class);
    for (SystemLogger systemLogger : serviceLoader) {
      systemLogger.configure();
    }
  }
}

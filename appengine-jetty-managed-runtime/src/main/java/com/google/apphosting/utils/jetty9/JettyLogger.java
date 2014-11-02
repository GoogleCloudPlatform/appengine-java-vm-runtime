/**
 * Copyright 2011 Google Inc. All Rights Reserved.
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


package com.google.apphosting.utils.jetty9;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;

import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * {@code JettyLogger} is a extension for {@link org.eclipse.jetty.util.log.JavaUtilLog}
 *
 */
public class JettyLogger extends JavaUtilLog {

  private static boolean logToApiProxy = Boolean.getBoolean("appengine.jetty.also_log_to_apiproxy");

  public JettyLogger() {
    this(null);
  }

  public JettyLogger(String name) {
    super("JettyLogger(" + name + ")");
  }

  public void warn(String msg, Throwable th) {
    super.warn(msg, th);

    if (logToApiProxy && ApiProxy.getCurrentEnvironment() != null && th != null) {
      ApiProxy.log(createLogRecord(msg, th));
    }
  }

  /**
   * Create a Child Logger of this Logger.
   */
  @Override
  protected Logger newLogger(String name) {
    return new JettyLogger(name);
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public String toString() {
    return getName();
  }

  private LogRecord createLogRecord(String message, Throwable ex) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    printWriter.println(message);
    if (ex != null) {
      ex.printStackTrace(printWriter);
    }

    return new LogRecord(LogRecord.Level.warn,
        System.currentTimeMillis() * 1000,
        stringWriter.toString());
  }
}

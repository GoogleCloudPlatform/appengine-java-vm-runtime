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

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * {@code AppEngineWebAppContext} is a customization of Jetty's {@link
 * WebAppContext} that is aware of the {@link ApiProxy} and can
 * provide custom logging and authentication.
 *
 */

@SuppressWarnings("unchecked")
public class AppEngineWebAppContext extends WebAppContext {
  protected static final int MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  private final String serverInfo;

  public AppEngineWebAppContext(String serverInfo) {
    this.serverInfo = serverInfo;
    init();
  }

  public AppEngineWebAppContext(File appDir, String serverInfo) {
    super(appDir.getPath(), URIUtil.SLASH);
    Resource webApp = null;
    try {
      webApp = Resource.newResource(appDir.getAbsolutePath());
      if (appDir.isDirectory()) {
        setWar(appDir.getPath());
        setBaseResource(webApp);
      } else {
        File extractedWebAppDir = createTempDir();
        extractedWebAppDir.mkdir();
        extractedWebAppDir.deleteOnExit();
        Resource jarWebWpp = JarResource.newJarResource(webApp);
        jarWebWpp.copyTo(extractedWebAppDir);
        setBaseResource(Resource.newResource(extractedWebAppDir.getAbsolutePath()));
        setWar(extractedWebAppDir.getPath());
      }
    } catch (Exception e) {
      throw new IllegalStateException("cannot create AppEngineWebAppContext:", e);
    }

    this.serverInfo = serverInfo;
    init();
  }

  private void init() {
    _scontext = new AppEngineServletContext();

    AppEngineAuthentication.configureSecurityHandler(
        (ConstraintSecurityHandler) getSecurityHandler());

    setMaxFormContentSize(MAX_RESPONSE_SIZE);
  }

  private static File createTempDir() {
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    String baseName = System.currentTimeMillis() + "-";

    for (int counter = 0; counter < 10; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        return tempDir;
      }
    }
    throw new IllegalStateException("Failed to create directory ");
  }

  public class AppEngineServletContext extends Context {

    @Override
    public ClassLoader getClassLoader() {
      return AppEngineWebAppContext.this.getClassLoader();
    }

    @Override
    public String getServerInfo() {
      return serverInfo;
    }

    @Override
    public void log(String message) {
      log(message, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param throwable an exception associated with this log message,
     * or {@code null}.
     */
    @Override
    public void log(String message, Throwable throwable) {
      StringWriter writer = new StringWriter();
      writer.append("javax.servlet.ServletContext log: ");
      writer.append(message);

      if (throwable != null) {
        writer.append("\n");
        throwable.printStackTrace(new PrintWriter(writer));
      }

      LogRecord.Level logLevel = throwable == null ? LogRecord.Level.info : LogRecord.Level.error;
      ApiProxy.log(new ApiProxy.LogRecord(logLevel, System.currentTimeMillis() * 1000L,
          writer.toString()));
    }

    @Override
    public void log(Exception exception, String msg) {
      log(msg, exception);
    }
  }
}

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
package com.google.apphosting.vmruntime.jetty9;

import static com.google.apphosting.vmruntime.jetty9.VmRuntimeTestBase.JETTY_HOME_PATTERN;
import java.io.File;
import java.io.IOException;
import org.apache.jasper.runtime.JspFactoryImpl;

import javax.servlet.jsp.JspFactory;
import org.eclipse.jetty.start.Main;

class JettyRunner implements Runnable {

  static final String JETTY9_XML_FILE_DIR = VmRuntimeTestBase.JETTY_HOME_PATTERN + "/etc";
  static final String LOG_FILE_PATTERN = /*TestUtil.getTmpDir() +*/ "log.%g";
  int port;

  public JettyRunner(int port) {
    this.port = port;
  }

  @Override
  public void run() {

    try {
      // We need to initialize this factory as it seems the Jetty start method does not call it.
      setSystemPropertiesForJetty();
      JspFactory.setDefaultFactory(new JspFactoryImpl());
      // Start jetty by calling main with the same arguments as when
      // we are starting it from the command line inside the VM.
      System.setProperty(
              "START", "javatests/com/google/apphosting/vmruntime/jetty9/start.config");
      // Make the file logger log to a directory the test can write to.
      System.setProperty(
              "com.google.apphosting.vmruntime.VmRuntimeFileLogHandler.pattern", LOG_FILE_PATTERN);

      org.eclipse.jetty.start.Main.main(new String[]{
        "/Users/ludo/a/appengine-java-vm-runtime/docker/etc/gae.xml"
     //     JETTY9_XML_FILE_DIR + "/jetty.xml",
      //     JETTY9_XML_FILE_DIR + "/jetty-http.xml",
      //    JETTY9_XML_FILE_DIR + "/jetty-deploy.xml"
      });

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Sets the system properties expected by jetty.xml.
   *
   * @throws IOException
   */
  protected void setSystemPropertiesForJetty() throws IOException {
    System.setProperty("org.eclipse.jetty.LEVEL", "DEBUG");
    System.setProperty("jetty.port", port + "");
    System.setProperty("jetty.host", "0.0.0.0");
    System.setProperty("jetty.appengineport", me.alexpanov.net.FreePortFinder.findFreeLocalPort() + "");
    System.setProperty("jetty.appenginehost", "localhost");
    System.setProperty("jetty.appengine.forwarded", "true");
    // Set the classloader to load the VM API proxy in the parent class loader so we can
    // access it from tests for easier mocking.
    System.setProperty("jetty_parent_classloader", "true");
    System.setProperty("jetty.home", JETTY_HOME_PATTERN);
    System.setProperty("jetty.logs",
            File.createTempFile("logs", "logs").getParentFile().getCanonicalPath());
  }
}

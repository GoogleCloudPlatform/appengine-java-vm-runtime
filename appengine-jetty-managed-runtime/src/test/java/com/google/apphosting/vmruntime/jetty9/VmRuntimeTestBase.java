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

import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;

/**
 * Base test class for the Java VmRuntime.
 *
 * Test methods that are Jetty version independent should be implemented in this class.
 */
public  class VmRuntimeTestBase extends TestCase {

  protected static final Logger logger = Logger.getLogger(VmRuntimeTestBase.class.getName());
  private static final String HOME_FOLDER = System.getProperty("HOME_FOLDER", "jetty_home");
  public static final String JETTY_HOME_PATTERN = ""//TestUtil.getRunfilesDir()
      + "com/google/apphosting/vmruntime/jetty9/" + HOME_FOLDER;


  // Wait at the most 30 seconds for Jetty to come up.
  private static final int JETTY_START_DELAY = 30;

  public static final String PROJECT = "google.com:test-project";
  public static final String PARTITION = "testpartition";
  public static final String VERSION = "testversion";
  public static final String BACKEND = "testbackend";
  public static final String INSTANCE = "frontend1";
  public static final String AFFINITY = "true";
  public static final String APPENGINE_HOSTNAME = "testhostname";

  public int port;
  public int externalPort;
  public String appengineWebXml = "WEB-INF/appengine-web.xml";

  /**
   * Returns the host:port the server is listening on externally. For VM Runtimes the local server
   * is listening on port 8080, but the requests can come in on either 80 or 443 depending on if
   * they are sent over https. For Jetty9 we are correcting the request port to account for this.
   *
   * @return The external port the request was sent to.
   */
  protected String getServerHost() {
    return "localhost" + (externalPort == 80 ? "" : ":" + externalPort);
  }

  /**
   * Creates a Localhost URL for accessing the Jetty instance under test.
   *
   * @param servletPath The path to request, for example "/test".
   * @return An URL object pointing at the local Jetty instance.
   * @throws MalformedURLException
   */
  protected URL createUrl(String servletPath) throws MalformedURLException {
    return new URL("http://localhost:" + port + servletPath);
  }


  /**
   * Creates a URL for accessing the Jetty instance under test, accessed by host ip.
   *
   * @param servletPath The path to request, for example "/test".
   * @return An URL object pointing at the local Jetty instance.
   * @throws MalformedURLException
   * @throws UnknownHostException
   */
  protected URL createUrlForHostIP(String servletPath)
      throws MalformedURLException, UnknownHostException {
    return new URL(
        "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port + servletPath);
  }

  /**
   * Convenience method for fetching a URL. Fails the test if the HTTP response code is not "OK".
   *
   * @param url The URL to fetch.
   * @return A string array of the lines in the response.
   * @throws IOException
   */
  protected String[] fetchUrl(URL url) throws IOException {
    return fetchUrlConnection((HttpURLConnection) url.openConnection());
  }

  /**
   * Convenience method for fetching from a HttpURLConnection. This allows headers to be set.
   * @param connection the connection to use
   * @return A string array of the lines in the response.
   * @throws IOException
   */
  protected String[] fetchUrlConnection(HttpURLConnection connection) throws IOException {
    connection.connect();
    int code = connection.getResponseCode();
    assertEquals(HttpServletResponse.SC_OK, code);
    ArrayList<String> lines = new ArrayList<>();
    String line;
    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    while ((line = in.readLine()) != null) {
      lines.add(line);
    }
    return lines.toArray(new String[lines.size()]);
  }

  protected String getUseMvmAgent() {
    return "false";
  }


  /**
   * Stub out the metadata cache so that any requests for metadata will be mocked locally and not
   * served from the metadata server (which only is available when running in an actual VM).
   */
  private void stubMetadataRequests() {
    int metadataPort = me.alexpanov.net.FreePortFinder.findFreeLocalPort();
    System.setProperty("metadata_server", "127.0.0.1:" + metadataPort);
    TestMetadataServer metadataServer = new TestMetadataServer(metadataPort);
    metadataServer.addMetadata(VmApiProxyEnvironment.PROJECT_ATTRIBUTE, PROJECT);
    metadataServer.addMetadata(VmApiProxyEnvironment.PARTITION_ATTRIBUTE, PARTITION);
    metadataServer.addMetadata(VmApiProxyEnvironment.BACKEND_ATTRIBUTE, BACKEND);
    metadataServer.addMetadata(VmApiProxyEnvironment.VERSION_ATTRIBUTE, VERSION);
    metadataServer.addMetadata(VmApiProxyEnvironment.INSTANCE_ATTRIBUTE, INSTANCE);
//    metadataServer.addMetadata(VmApiProxyEnvironment.AFFINITY_ATTRIBUTE, AFFINITY);
    metadataServer.addMetadata(
        VmApiProxyEnvironment.APPENGINE_HOSTNAME_ATTRIBUTE, APPENGINE_HOSTNAME);
 //   metadataServer.addMetadata(
 //       VmApiProxyEnvironment.USE_MVM_AGENT_ATTRIBUTE, getUseMvmAgent());
    Thread metadataThread = new Thread(metadataServer);
    metadataThread.setName("Metadata server");
    metadataThread.setDaemon(true);
    metadataThread.start();
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    port = me.alexpanov.net.FreePortFinder.findFreeLocalPort();
    externalPort = port;
    stubMetadataRequests();
    // Start jetty using the Runnable configured by the sub class.
    JettyRunner runner = new JettyRunner(port);
    runner.setAppEngineWebXml(appengineWebXml);
    Thread jettyRunnerThread = new Thread(runner);
    jettyRunnerThread.setName("JettyRunnerThread");
    jettyRunnerThread.setDaemon(true);
    jettyRunnerThread.start();
    runner.waitForStarted(JETTY_START_DELAY, TimeUnit.SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {

    super.tearDown();
  }

}
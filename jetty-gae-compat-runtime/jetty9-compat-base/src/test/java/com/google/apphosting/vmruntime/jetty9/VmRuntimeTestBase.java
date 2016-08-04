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

package com.google.apphosting.vmruntime.jetty9;

import static com.google.apphosting.vmruntime.VmMetadataCache.DEFAULT_META_DATA_SERVER;
import static com.google.apphosting.vmruntime.VmMetadataCache.META_DATA_PATTERN;

import junit.framework.TestCase;

import org.junit.Ignore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * <p>
 * Test methods that are Jetty version independent should be implemented in this class.
 */
@Ignore
public class VmRuntimeTestBase extends TestCase {

  protected static final Logger logger = Logger.getLogger(VmRuntimeTestBase.class.getName());
  private static final String HOME_FOLDER = System.getProperty("HOME_FOLDER", "jetty_home");
  public static final String JETTY_HOME_PATTERN =
      "" //TestUtil.getRunfilesDir()
          + "com/google/apphosting/vmruntime/jetty9/"
          + HOME_FOLDER;

  // Wait at the most 30 seconds for Jetty to come up.
  private static final int JETTY_START_DELAY = 45;

  public int port;
  public int externalPort;
  public String appengineWebXml = "WEB-INF/appengine-web.xml";
  TestMetadataServer metadataServer;
  JettyRunner runner;

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
   */
  protected URL createUrl(String servletPath) throws MalformedURLException {
    return new URL("http://localhost:" + port + servletPath);
  }

  /**
   * Creates a URL for accessing the Jetty instance under test, accessed by host ip.
   *
   * @param servletPath The path to request, for example "/test".
   * @return An URL object pointing at the local Jetty instance.
   */
  protected URL createUrlForHostIp(String servletPath)
      throws MalformedURLException, UnknownHostException {
    return new URL(
        "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port + servletPath);
  }

  /**
   * Convenience method for fetching a URL. Fails the test if the HTTP response code is not "OK".
   *
   * @param url The URL to fetch.
   * @return A string array of the lines in the response.
   */
  protected String[] fetchUrl(URL url) throws IOException {
    return fetchUrlConnection((HttpURLConnection) url.openConnection());
  }

  /**
   * Convenience method for fetching from a HttpURLConnection. This allows headers to be set.
   *
   * @param connection the connection to use
   * @return A string array of the lines in the response.
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

  protected final String getUseMvmAgent() {
    return "false";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    port = JettyRunner.findAvailablePort();
    externalPort = port;
    metadataServer = new TestMetadataServer();
    metadataServer.setUseMvm(Boolean.valueOf(getUseMvmAgent()));
    metadataServer.start();

    // Start jetty using the Runnable configured by the sub class.
    runner = new JettyRunner(port);

    runner.setAppEngineWebXml(appengineWebXml);
    Thread jettyRunnerThread = new Thread(runner);
    jettyRunnerThread.setName("JettyRunnerThread");
    jettyRunnerThread.setDaemon(true);
    jettyRunnerThread.start();
    runner.waitForStarted(JETTY_START_DELAY, TimeUnit.SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {
    runner.stop();
    metadataServer.stop();
    super.tearDown();
    Thread.sleep(50);
  }

  protected HttpURLConnection openConnection(String path) throws IOException {
    String server = System.getProperty("metadata_server", DEFAULT_META_DATA_SERVER);
    URL url = new URL(String.format(META_DATA_PATTERN, server, path));
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Metadata-Flavor", "Google");
    return conn;
  }

  /**
   * Timeout in milliseconds to retrieve data from the server.
   */
  private static final int TIMEOUT_MILLIS = 120 * 1000;

  protected String getMetadataFromServer(String path) throws IOException {
    BufferedReader reader = null;
    HttpURLConnection connection = null;
    try {
      connection = openConnection(path);
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      StringBuffer result = new StringBuffer();
      char[] buffer = new char[4096];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        result.append(buffer, 0, read);
      }
      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
        return result.toString().trim();
      } else if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        return null;
      }
      throw new IOException(
          "Meta-data request for '"
              + path
              + "' failed with error: "
              + connection.getResponseMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          logger.info("Error closing connection for " + path + ": " + e.getMessage());
        }
      }
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}

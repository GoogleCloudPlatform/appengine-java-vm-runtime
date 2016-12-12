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

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

/**
 * Misc individual Jetty9 vmengines tests.
 */
public class VmRuntimeJettyKitchenSinkIT extends VmRuntimeTestBase {

  @Override
  protected void setUp() throws Exception {
    appengineWebXml = "WEB-INF/sessions-disabled-appengine-web.xml";
    super.setUp();
  }

  /**
   * Test that non compiled jsp files can be served.
   */
  public void testJspNotCompiled() throws Exception {
    int iter = 0;
    int end = 6;
    String[] lines =
        fetchUrl(createUrl(String.format("/hello_not_compiled.jsp?start=%d&end=%d", iter, end)));
    String iterationFormat = "<h2>Iteration %d</h2>";
    for (String line : lines) {
      if (!line.contains("Iteration")) {
        continue;
      }
      assertEquals(line.trim(), String.format(iterationFormat, iter++));
    }
    assertEquals(end + 1, iter);
  }

  /**
   * Test that non compiled jstl JSP  can be served.
   */
  public void testJstlJSP() throws Exception {
    String[] lines = fetchUrl(createUrl("/jstl.jsp"));
    int max = -1;
    int count = 0;
    for (String line : lines) {
      line = line.trim();
      if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
        int value = Integer.parseInt(line);
        count++;
        if (value > max) {
          max = value;
        }
      }
    }
    assertEquals(10, count);
    assertEquals(10, max);
  }

  /**
   * Tests that mapping a servlet to / works.
   */
  public void testWelcomeServlet() throws Exception {
    String[] lines = fetchUrl(createUrl("/"));
    assertTrue(Arrays.asList(lines).contains("Hello, World!"));
  }

  /**
   * Tests that app.yaml is protected
   */
  public void testAppYamlHidden() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) createUrl("/app.yaml").openConnection();
    connection.connect();
    assertEquals(404, connection.getResponseCode());
  }

  /**
   * Test that the API Proxy was configured by the VmRuntimeFilter.
   */
  public void testApiProxyInstall() throws Exception {
    assertNotNull(ApiProxy.getDelegate());
    assertEquals(
        VmApiProxyDelegate.class.getCanonicalName(),
        ApiProxy.getDelegate().getClass().getCanonicalName());
  }

  /**
   * Test that the thread local environment is set up on each request.
   */
  public void testEnvironmentInstall() throws Exception {
    String[] lines = fetchUrl(createUrl("/CurrentEnvironmentAccessor"));
    List<String> expectedLines =
        Arrays.asList("testpartition~google.com:test-project", "testbackend", "testversion.0");
    assertEquals(expectedLines, Arrays.asList(lines));
  }

  /**
   * Test that the health check servlet was loaded and responds with "ok" with
   * the proper version provided.
   */
  public void testHealthOk() throws Exception {
    String[] lines = fetchUrl(createUrl("/_ah/health"));
    assertEquals(1, lines.length);
    assertEquals("ok", lines[0].trim());
  }

  /**
   * Test that all AppEngine specific system properties are set up when the
   * VmRuntimeFilter is initialized.
   */
  public void testSystemProperties() throws Exception {
    String[] lines = fetchUrl(createUrl("/printSystemProperties"));
    assertEquals(7, lines.length);
    assertEquals("sysprop1 value", lines[0]);
    assertEquals("sysprop2 value", lines[1]);
    assertEquals("null", lines[2]);
    assertEquals(SystemProperty.Environment.Value.Production.name(), lines[3]);
    assertTrue(lines[4].startsWith("Google App Engine/"));
    assertEquals(TestMetadataServer.PROJECT, lines[5]);
    assertEquals(TestMetadataServer.VERSION + ".0", lines[6]);
  }

  /**
   * Test that the warmup handler is installed.
   */
  public void testWarmup() throws Exception {
    String[] lines = fetchUrl(createUrl("/_ah/warmup")); // fetchUrl() fails on non-OK return codes.
    assertEquals(0, lines.length);
  }

  /**
   * Test that sessions are disabled. Disabling sessions means that the default HashSessionManager
   * is being used, which keeps sessions in memory only. Enabling sessions uses the appengine
   * SessionManager which will use Datastore and memcache as persistent backing stores.
   */
  public void testSessions() throws Exception {
    for (int i = 1; i <= 5; i++) {
      String[] lines = fetchUrl(createUrl("/count?type=session"));
      assertEquals(1, lines.length);
      assertEquals(
          "1",
          lines[0]); // We're not passing in any session cookie so each request is a fresh session.
    }
  }

  public void testSsl_NoSsl() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/test-ssl").toString());
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    String expected = "false:http:http://localhost:" + port + "/test-ssl";
    assertEquals(expected, get.getResponseBodyAsString());
  }

  public void testSsl_WithSsl() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/test-ssl").toString());
    get.addRequestHeader(VmApiProxyEnvironment.HTTPS_HEADER, "on");
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("true:https:https://localhost/test-ssl", get.getResponseBodyAsString());
  }

  public void testWithUntrustedInboundIp() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrlForHostIp("/test-ssl").toString());
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
  }

  protected int fetchResponseCode(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.connect();
    return connection.getResponseCode();
  }

  public void testShutDown() throws Exception {

    int code = fetchResponseCode(createUrl("/_ah/health"));
    assertEquals(HttpServletResponse.SC_OK, code);

    // Send a request to /_ah/stop to trigger lameduck.
    String[] lines = fetchUrl(createUrl("/_ah/stop"));
    assertEquals(1, lines.length);
    assertEquals("ok", lines[0].trim());

    code = fetchResponseCode(createUrl("/_ah/health"));
    assertEquals(HttpServletResponse.SC_BAD_GATEWAY, code);
  }
}

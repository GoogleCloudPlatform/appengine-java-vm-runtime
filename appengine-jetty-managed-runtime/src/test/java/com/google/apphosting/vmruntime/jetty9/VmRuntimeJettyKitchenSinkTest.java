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

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;
import com.google.apphosting.vmruntime.VmRuntimeUtils;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Misc individual Jetty9 vmengines tests.
 *
 */
public class VmRuntimeJettyKitchenSinkTest extends VmRuntimeTestBase {

  @Override
  protected void setUp() throws Exception {
    appengineWebXml = "WEB-INF/sessions-disabled-appengine-web.xml";
    File webinf = new File("./WEB-INF");
    if (!webinf.exists() || !webinf.isDirectory()) {
      webinf = new File("./target/webapps/testwebapp/WEB-INF");
      if (!webinf.exists() || !webinf.isDirectory()) {
        System.err.println(webinf.getAbsolutePath());
        throw new IllegalStateException("Incorrect working directory " + new File(".").getAbsoluteFile().getCanonicalPath());
      }
    }

    File small = new File(webinf.getParentFile(), "small.txt");
    if (!small.exists()) {
      Writer out = new OutputStreamWriter(new FileOutputStream(small));
      out.write("zero\n");
      out.write("one\n");
      out.write("two\n");
      out.close();
    }

    File big = new File(webinf.getParentFile(), "big.txt");
    if (!big.exists()) {
      Writer out = new OutputStreamWriter(new FileOutputStream(big));
      for (int i = 0; i < 4096; i++) {
        out.write(Integer.toString(i));
        out.write("\n");
      }
      out.close();
    }

    File huge = new File(webinf.getParentFile(), "huge.txt");
    if (!huge.exists()) {
      Writer out = new OutputStreamWriter(new FileOutputStream(huge));
      for (int i = 0; i < 16 * 1024 * 1024; i++) {
        out.write(Integer.toString(i));
        out.write("\n");
      }
      out.close();
    }

		super.setUp();
	}

	/**
   * Test that non compiled jsp files can be served.
   *
   * @throws Exception
   */
  public void testJspNotCompiled() throws Exception {
    int iter = 0;
    int end = 6;
    String[] lines
            = fetchUrl(createUrl(String.format("/hello_not_compiled.jsp?start=%d&end=%d", iter, end)));
    String iterationFormat = "<h2>Iteration %d</h2>";
    for (String line : lines) {
      System.out.println(line);
      if (!line.contains("Iteration")) {
        continue;
      }
      assertEquals(line.trim(), String.format(iterationFormat, iter++));
    }
    assertEquals(end + 1, iter);
  }

  /**
   * Tests that mapping a servlet to / works.
   *
   * @throws Exception
   */
  public void testWelcomeServlet() throws Exception {
    String[] lines = fetchUrl(createUrl("/"));
    assertTrue(Arrays.asList(lines).contains("Hello, World!"));
  }

  public void testSmallTxt() throws Exception {
    String[] lines = fetchUrl(createUrl("/small.txt"));
    assertEquals("zero", lines[0]);
    assertEquals("one", lines[1]);
    assertEquals("two", lines[2]);
  }

  public void testBigTxt() throws Exception {
    String[] lines = fetchUrl(createUrl("/big.txt"));
    assertEquals("0", lines[0]);
    assertEquals("4095", lines[4095]);
  }

  public void ignore_testHugeTxt() throws Exception {
    System.err.println("Expect: java.io.IOException: Max response size exceeded.");
    HttpURLConnection connection = (HttpURLConnection) createUrl("/huge.txt").openConnection();
    connection.connect();
    assertEquals(500, connection.getResponseCode());
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
   *
   * @throws Exception
   */
  public void testApiProxyInstall() throws Exception {
    assertNotNull(ApiProxy.getDelegate());
    assertEquals(VmApiProxyDelegate.class.getCanonicalName(),
            ApiProxy.getDelegate().getClass().getCanonicalName());
  }

  /**
   * Test that the thread local environment is set up on each request.
   *
   * @throws Exception
   */
  public void testEnvironmentInstall() throws Exception {
    String[] lines = fetchUrl(createUrl("/CurrentEnvironmentAccessor"));
    List<String> expectedLines = Arrays.asList(
            "testpartition~google.com:test-project",
            "testbackend",
            "testversion.0");
    assertEquals(expectedLines, Arrays.asList(lines));
  }

  /**
   * Test that the health check servlet was loaded and responds with "ok" with
   * the proper version provided.
   *
   * @throws Exception
   */
  public void testHealthOK() throws Exception {
    String[] lines = fetchUrl(createUrl("/_ah/health"));
    assertEquals(1, lines.length);
    assertEquals("ok", lines[0].trim());
  }

    public void testAsyncRequests_WaitUntilDone() throws Exception {
    long sleepTime = 2000;
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
    GetMethod get = new GetMethod(createUrl("/sleep").toString());
    get.addRequestHeader("Use-Async-Sleep-Api", "true");
    get.addRequestHeader("Sleep-Time", Long.toString(sleepTime));
    long startTime = System.currentTimeMillis();
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    Header vmApiWaitTime = get.getResponseHeader(VmRuntimeUtils.ASYNC_API_WAIT_HEADER);
    assertNotNull(vmApiWaitTime);
    assertTrue(Integer.parseInt(vmApiWaitTime.getValue()) > 0);
    long elapsed = System.currentTimeMillis() - startTime;
    assertTrue(elapsed >= sleepTime);
  }

  /**
   * Test that all AppEngine specific system properties are set up when the
   * VmRuntimeFilter is initialized.
   *
   * @throws Exception
   */
  public void testSystemProperties() throws Exception {
    String[] lines = fetchUrl(createUrl("/printSystemProperties"));
    assertEquals(7, lines.length);
    assertEquals("sysprop1 value", lines[0]);
    assertEquals("sysprop2 value", lines[1]);
    assertEquals("null", lines[2]);
    assertEquals(SystemProperty.Environment.Value.Production.name(), lines[3]);
    assertTrue(lines[4].startsWith("Google App Engine/"));
    assertEquals(PROJECT, lines[5]);
    assertEquals(VERSION + ".0", lines[6]);
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
   * is being used, which keeps sessions in memory only. Enabling sessions uses the appengine SessionManager
   * which will use Datastore and memcache as persistent backing stores.
   *
   * @throws Exception
   */
  public void testSessions() throws Exception {
    for (int i = 1; i <= 5; i++) {
      String[] lines = fetchUrl(createUrl("/count?type=session"));
      assertEquals(1, lines.length);
      assertEquals("1", lines[0]); // We're not passing in any session cookie so each request is a fresh session.
    }
  }

  public void testSsl_NoSSL() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/test-ssl").toString());
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    String expected = "false:http:http://localhost:"+port+"/test-ssl";
    assertEquals(expected, get.getResponseBodyAsString());
  }

  public void testSsl_WithSSL() throws Exception {
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
    GetMethod get = new GetMethod(createUrlForHostIP("/test-ssl").toString());
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

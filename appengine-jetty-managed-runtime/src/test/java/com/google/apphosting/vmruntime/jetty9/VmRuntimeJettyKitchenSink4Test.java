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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * Misc individual Jetty9 vmengines tests.
 *
 */
public class VmRuntimeJettyKitchenSink4Test extends VmRuntimeTestBase {

  /**
   * Test that sessions are disabled. Tests that enable sessions should override
   * this method.
   *
   * @throws Exception
   */
  public void testSessions() throws Exception {
    for (int i = 1; i <= 5; i++) {
      String[] lines = fetchUrl(createUrl("/count?type=session"));
      assertEquals(1, lines.length);
      assertEquals("-1", lines[0]); // When sessions are disabled we should get -1 every time.
    }
  }

  public void testSsl_NoSSL() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/test-ssl").toString());
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("false:http:http://localhost/test-ssl", get.getResponseBodyAsString());
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
}

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
import com.google.apphosting.vmruntime.VmRuntimeUtils;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * Misc individual Jetty9 vmengines tests.
 *
 */
public class VmRuntimeJettyKitchenSink5Test extends VmRuntimeTestBase {

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
    assertEquals("Google App Engine/unknown", lines[4]);
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
}

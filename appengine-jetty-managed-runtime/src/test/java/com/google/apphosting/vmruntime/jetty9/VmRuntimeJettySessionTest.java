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

import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse.Item;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DatastorePb.PutResponse;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.io.IOException;
import java.net.HttpURLConnection;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.net.URL;
import java.util.Arrays;

import javax.servlet.http.HttpServletResponse;

/**
 * Tests for running AppEngine Java apps inside a VM using a Jetty 9 container
 * with HTTP servlet sessions enabled.
 *
 * @author isdal@google.com (Tomas Isdal)
 */
public class VmRuntimeJettySessionTest extends VmRuntimeTestBase {

  @Override
  protected void setUp() throws Exception {
  	appengineWebXml = "WEB-INF/sessions-enabled-appengine-web.xml";
    externalPort = 80;
    super.setUp();
  }

  protected int fetchResponseCode(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.connect();
    return connection.getResponseCode();
  }

 

  public void TODOLUDOtestSsl_NoSSL() throws Exception {
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

  public void LUDOTODOtestWithInvalidInboundIp() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrlForHostIP("/test-ssl").toString());
    int httpCode = httpClient.executeMethod(get);
    assertEquals(403, httpCode);
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

//  public void testHealthCheckInterval() throws Exception {
//    // Test that it was not healthy without IsLastSuccessful query string.
//    int code = fetchResponseCode(createUrl("/_ah/health"));
//    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, code);
//
//    // Test that it will be healthy if the last IsLastSuccessful query string was less than
//    // VmRuntimeWebAppContext.checkIntervalSec ago.
//    String[] lines = fetchUrl(createUrl("/_ah/health?IsLastSuccessful=yes"));
//    assertEquals(1, lines.length);
//    assertEquals("ok", lines[0].trim());
//
//    Thread.sleep((VmRuntimeWebAppContext.checkIntervalSec - 1) * 1000);
//
//    code = fetchResponseCode(createUrl("/_ah/health"));
//    assertEquals(HttpServletResponse.SC_OK, code);
//
//    // Test that it will be unhealthy if the last IsLastSuccessful query string was more than
//    // VmRuntimeWebAppContext.checkIntervalSec ago.
//    lines = fetchUrl(createUrl("/_ah/health?IsLastSuccessful=yes"));
//    assertEquals(1, lines.length);
//    assertEquals("ok", lines[0].trim());
//
//    Thread.sleep(VmRuntimeWebAppContext.checkIntervalSec * 1000);
//
//    code = fetchResponseCode(createUrl("/_ah/health"));
//    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, code);
//  }
  /**
   * Create a datastore put response with the minimal fields required to make
   * the put succeed.
   *
   * @return A new PutReponse object.
   */
  private PutResponse createDatastorePutResponse() {
    PutResponse putResponse = new PutResponse();
    Reference key = putResponse.addKey();
    key.setApp(VmRuntimeTestBase.PROJECT);
    Path path = key.getMutablePath();
    Element element = path.addElement();
    element.setType("kind");
    element.setName("name");
    return putResponse;
  }

  /**
   * Test that sessions are persisted in the datastore and memcache, and that
   * any data stored is available to subsequent requests.
   *
   * @throws Exception
   */
  public void testSessions() throws Exception {
    URL url = createUrl("/count?type=session");

    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);

    // Add responses for session create.
    fakeApiProxy.addApiResponse(createDatastorePutResponse());
    fakeApiProxy.addApiResponse(
            MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());
    // Add responses for session save.
    fakeApiProxy.addApiResponse(createDatastorePutResponse());
    fakeApiProxy.addApiResponse(
            MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());

    // Make a call to count and save the session cookie.
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod firstGet = new GetMethod(url.toString());
    int returnCode = httpClient.executeMethod(firstGet);
    assertEquals(HttpServletResponse.SC_OK, returnCode);
    // Check the count, should be 1 for the first request.
    assertEquals("1", firstGet.getResponseBodyAsString().trim());

    // Parse the memcache put request so we can respond to the next get request.
    MemcacheSetRequest setRequest
            = MemcacheSetRequest.parseFrom(fakeApiProxy.getLastRequest().getRequestData());
    assertEquals(1, setRequest.getItemCount());
    Item responsePayload = Item.newBuilder()
            .setKey(setRequest.getItem(0).getKey()).setValue(setRequest.getItem(0).getValue()).build();
    MemcacheGetResponse getResponse
            = MemcacheGetResponse.newBuilder().addItem(responsePayload).build();
    fakeApiProxy.addApiResponse(getResponse);

    // Add responses for session save.
    fakeApiProxy.addApiResponse(createDatastorePutResponse());
    fakeApiProxy.addApiResponse(
            MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());

    // Make a call to count with the session cookie.
    GetMethod secondGet = new GetMethod(url.toString());
    returnCode = httpClient.executeMethod(secondGet);
    assertEquals(HttpServletResponse.SC_OK, returnCode);
    // Check the count, should be "2" for the second request.
    assertEquals("2", secondGet.getResponseBodyAsString().trim());
  }

}

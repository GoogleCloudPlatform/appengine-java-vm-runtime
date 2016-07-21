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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.CreateLoginURLResponse;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * Testing Jetty9 auth handling.
 */
public class VmRuntimeJettyAuthIT extends VmRuntimeTestBase {

  public void testAuth_UserNotRequired() throws Exception {
    String[] lines = fetchUrl(createUrl("/test-auth"));
    assertEquals(1, lines.length);
    assertEquals("null: null", lines[0].trim());
  }

  public void testAuth_UserRequiredNoUser() throws Exception {
    String loginUrl = "http://login-url?url=http://test-app.googleapp.com/user/test-auth";
    CreateLoginURLResponse loginUrlResponse = new CreateLoginURLResponse();
    loginUrlResponse.setLoginUrl(loginUrl);
    // Fake the expected call to "user/CreateLoginUrl".
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);
    fakeApiProxy.addApiResponse(loginUrlResponse);

    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/user/test-auth").toString());
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(302, httpCode);
    Header redirUrl = get.getResponseHeader("Location");
    assertEquals(loginUrl, redirUrl.getValue());
  }

  public void testAuth_UserRequiredWithUser() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/user/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.EMAIL_HEADER, "isdal@google.com");
    get.addRequestHeader(VmApiProxyEnvironment.AUTH_DOMAIN_HEADER, "google.com");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("isdal@google.com: isdal@google.com", get.getResponseBodyAsString());
  }

  public void testAuth_UserRequiredWithAdmin() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/user/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.EMAIL_HEADER, "isdal@google.com");
    get.addRequestHeader(VmApiProxyEnvironment.AUTH_DOMAIN_HEADER, "google.com");
    get.addRequestHeader(VmApiProxyEnvironment.IS_ADMIN_HEADER, "1");

    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("isdal@google.com: isdal@google.com", get.getResponseBodyAsString());
  }

  public void testAuth_AdminRequiredWithNonAdmin() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.EMAIL_HEADER, "isdal@google.com");
    get.addRequestHeader(VmApiProxyEnvironment.AUTH_DOMAIN_HEADER, "google.com");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(403, httpCode);
  }

  public void testAuth_AdminRequiredNoUser() throws Exception {
    String loginUrl = "http://login-url?url=http://test-app.googleapp.com/user/test-auth";
    CreateLoginURLResponse loginUrlResponse = new CreateLoginURLResponse();
    loginUrlResponse.setLoginUrl(loginUrl);
    // Fake the expected call to "user/CreateLoginUrl".
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);
    fakeApiProxy.addApiResponse(loginUrlResponse);

    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(302, httpCode);
    Header redirUrl = get.getResponseHeader("Location");
    assertEquals(loginUrl, redirUrl.getValue());
  }

  public void testAuth_AdminRequiredWithAdmin() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.EMAIL_HEADER, "isdal@google.com");
    get.addRequestHeader(VmApiProxyEnvironment.AUTH_DOMAIN_HEADER, "google.com");
    get.addRequestHeader(VmApiProxyEnvironment.IS_ADMIN_HEADER, "1");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("isdal@google.com: isdal@google.com", get.getResponseBodyAsString());
  }

  public void testAuth_AdminRequiredNoUser_SkipAdminCheck() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.addRequestHeader("X-Google-Internal-SkipAdminCheck", "1");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("null: null", get.getResponseBodyAsString());
  }

  public void testAuth_AdminRequiredNoUser_TaskQueueHeader() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.addRequestHeader("X-AppEngine-QueueName", "default");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("null: null", get.getResponseBodyAsString());
  }

  public void testAuth_UntrustedInboundIp() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrlForHostIp("/admin/test-auth").toString());
    get.addRequestHeader(
        VmApiProxyEnvironment.REAL_IP_HEADER, "127.0.0.2"); // Force untrusted dev IP
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(307, httpCode);
    assertEquals(
        "https://testversion-dot-testbackend-dot-testhostname/admin/test-auth",
        get.getResponseHeader("Location").getValue());
  }

  public void testAuth_UntrustedInboundIpWithQuery() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrlForHostIp("/admin/test-auth?foo=bar").toString());
    get.addRequestHeader(
        VmApiProxyEnvironment.REAL_IP_HEADER, "127.0.0.2"); // Force untrusted dev IP
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(307, httpCode);
    assertEquals(
        "https://testversion-dot-testbackend-dot-testhostname/admin/test-auth?foo=bar",
        get.getResponseHeader("Location").getValue());
  }

  public void testAuth_TrustedRealIp() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrlForHostIp("/admin/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.REAL_IP_HEADER, "127.0.0.1");
    get.addRequestHeader(VmApiProxyEnvironment.EMAIL_HEADER, "isdal@google.com");
    get.addRequestHeader(VmApiProxyEnvironment.AUTH_DOMAIN_HEADER, "google.com");
    get.addRequestHeader(VmApiProxyEnvironment.IS_ADMIN_HEADER, "1");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(200, httpCode);
    assertEquals("isdal@google.com: isdal@google.com", get.getResponseBodyAsString());
  }

  public void testAuth_UntrustedRealIp() throws Exception {
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    GetMethod get = new GetMethod(createUrl("/admin/test-auth").toString());
    get.addRequestHeader(VmApiProxyEnvironment.REAL_IP_HEADER, "123.123.123.123");
    get.setFollowRedirects(false);
    int httpCode = httpClient.executeMethod(get);
    assertEquals(307, httpCode);
    assertEquals(
        "https://testversion-dot-testbackend-dot-testhostname/admin/test-auth",
        get.getResponseHeader("Location").getValue());
  }
}

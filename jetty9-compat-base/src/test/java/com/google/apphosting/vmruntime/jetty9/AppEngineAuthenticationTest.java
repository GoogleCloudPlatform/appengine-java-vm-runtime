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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;

import junit.framework.TestCase;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.security.Constraint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

/**
 * Tests for AppEngineAuthentication.
 *
 * <p>Test plan: Create and configure a ConstraintSecurityHandler to use our AppEngineAuthentication
 * classes, then configure some paths to require users to be logged in. We then fire requests at the
 * security handler by calling its handle() method and verify that unauthenticated requests are
 * redirected to a login url, while authenticated requests are allowed through.
 */
public class AppEngineAuthenticationTest extends TestCase {
  private static final String SERVER_NAME = "testapp.appspot.com";
  private static final String USER_EMAIL = "user@gmail.com";
  private static final String ADMIN_EMAIL = "isdal@gmail.com";
  private static final String TEST_ENV_DOMAIN = "gmail.com";

  private class MockAddressChecker implements VmRuntimeTrustedAddressChecker {
    @Override
    public boolean isTrustedRemoteAddr(String remoteAddr) {
      return true;
    }
  }

  /**
   * Version of ConstraintSecurityHandler that allows doStart to be called from outside of package.
   */
  private static class TestConstraintSecurityHandler extends ConstraintSecurityHandler {
    // Override so we can call doStart to initialize the mapping;
    @Override
    public void doStart() throws Exception {
      super.doStart();
    }
  }

  private void addConstraint(
      ConstraintSecurityHandler handler, String path, String name, String... roles) {
    Constraint constraint = new Constraint();
    constraint.setName(name);
    constraint.setRoles(roles);
    constraint.setAuthenticate(true);
    ConstraintMapping mapping = new ConstraintMapping();
    mapping.setMethod("GET");
    mapping.setPathSpec(path);
    mapping.setConstraint(constraint);
    handler.addConstraintMapping(mapping);
  }

  private LocalServiceTestHelper helper;
  private TestConstraintSecurityHandler securityHandler;

  @Override
  public void setUp() throws Exception {
    // Initialize a Security handler and install our authenticatior.
    ServletHandler servletHandler = new ServletHandler();
    servletHandler.addServletWithMapping(new ServletHolder(new AuthServlet()) {}, "/*");
    securityHandler = new TestConstraintSecurityHandler();
    securityHandler.setHandler(servletHandler);
    AppEngineAuthentication.configureSecurityHandler(securityHandler, new MockAddressChecker());

    // Add authenticated paths to the security handler. Requests for those paths will be forwarded
    // to the authenticator with "mandatory=true".
    addConstraint(securityHandler, "/admin/*", "adminOnly", "admin");
    addConstraint(securityHandler, "/user/*", "userOnly", "*");
    addConstraint(securityHandler, "/_ah/login", "reserved", "*", "admin");
    securityHandler.doStart(); // Start the handler so the constraint map is compiled.

    // Use a local test version of the UserService to allow us to control login state.
    helper = new LocalServiceTestHelper(new LocalUserServiceTestConfig()).setUp();
  }

  @Override
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  /**
   * Fire one request at the security handler (and by extension to the AuthServlet behind it).
   *
   * @param path     The path to hit.
   * @param request  The request object to use.
   * @param response The response object to use. Must be created by Mockito.mock()
   * @return Any data written to response.getWriter()
   */
  private String runRequest(String path, Request request, Response response)
      throws IOException, ServletException {
    //request.setMethod(/*HttpMethod.GET,*/ "GET");
    HttpURI uri = new HttpURI("http", SERVER_NAME, 9999, path);
    HttpFields httpf = new HttpFields();
    MetaData.Request metadata = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, httpf);
    request.setMetaData(metadata);

    // request.setServerName(SERVER_NAME);
    // request.setAuthority(SERVER_NAME,9999);
    //// request.setPathInfo(path);
    //// request.setURIPathQuery(path);
    request.setDispatcherType(DispatcherType.REQUEST);
    doReturn(response).when(request).getResponse();

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintWriter writer = new PrintWriter(output)) {
      when(response.getWriter()).thenReturn(writer);
      securityHandler.handle(path, request, request, response);
    }
    return new String(output.toByteArray());
  }

  private String runRequest2(String path, Request request, Response response)
      throws IOException, ServletException {
    //request.setMethod(/*HttpMethod.GET,*/ "GET");
    HttpURI uri = new HttpURI("http", SERVER_NAME, 9999, path);
    HttpFields httpf = new HttpFields();
    MetaData.Request metadata = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, httpf);
    //   request.setMetaData(metadata);

    // request.setServerName(SERVER_NAME);
    // request.setAuthority(SERVER_NAME,9999);
    //// request.setPathInfo(path);
    //// request.setURIPathQuery(path);
    request.setDispatcherType(DispatcherType.REQUEST);
    doReturn(response).when(request).getResponse();

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintWriter writer = new PrintWriter(output)) {
      when(response.getWriter()).thenReturn(writer);
      securityHandler.handle(path, request, request, response);
    }
    return new String(output.toByteArray());
  }

  public void testUserNotRequired() throws Exception {
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String path = "/";
    String output = runRequest(path, request, response);
    assertEquals("null: null", output);
  }

  public void testUserNotRequired_WithUser() throws Exception {
    // Add a logged in user.
    helper.setEnvIsLoggedIn(true).setEnvEmail(USER_EMAIL).setEnvAuthDomain(TEST_ENV_DOMAIN).setUp();
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String path = "/";
    String output = runRequest(path, request, response);
    assertEquals(String.format("%s: %s", USER_EMAIL, USER_EMAIL), output);
  }

  public void testUserNotRequired_WithAdmin() throws Exception {
    // Add a logged in admin.
    helper
        .setEnvIsLoggedIn(true)
        .setEnvIsAdmin(true)
        .setEnvEmail(ADMIN_EMAIL)
        .setEnvAuthDomain(TEST_ENV_DOMAIN)
        .setUp();
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String path = "/";
    String output = runRequest(path, request, response);
    assertEquals(String.format("%s: %s", ADMIN_EMAIL, ADMIN_EMAIL), output);
  }

  public void testUserRequired_NoUser() throws Exception {
    String path = "/user/blah";
    Request request = spy(new Request(null, null));
    //request.setServerPort(9999);
    HttpURI uri = new HttpURI("http", SERVER_NAME, 9999, path);
    HttpFields httpf = new HttpFields();
    MetaData.Request metadata = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, httpf);
    request.setMetaData(metadata);
    // request.setAuthority(SERVER_NAME,9999);
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    // Verify that the servlet never was run (there is no output).
    assertEquals("", output);
    // Verify that the request was redirected to the login url.
    String loginUrl =
        UserServiceFactory.getUserService()
            .createLoginURL(String.format("http://%s%s", SERVER_NAME + ":9999", path));
    verify(response).sendRedirect(loginUrl);
  }

  public void testUserRequired_NoUserButLoginUrl() throws Exception {
    String path = "/_ah/login";
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    // Verify that the request made it to the login url.
    assertEquals("null: null", output);
  }

  public void testUserRequired_PreserveQueryParams() throws Exception {
    String path = "/user/blah";

    Request request = new Request(null, null);
    // request.setServerPort(9999);
    HttpURI uri = new HttpURI("http", SERVER_NAME, 9999, path, "foo=baqr", "foo=bar", "foo=barff");
    HttpFields httpf = new HttpFields();
    MetaData.Request metadata = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, httpf);
    request.setMetaData(metadata);
    MultiMap<String> queryParameters = new MultiMap<>();
    queryParameters.add("ffo", "bar");
    request.setQueryParameters(queryParameters);
    request = spy(request);

    /// request.setAuthority(SERVER_NAME,9999);
    request.setQueryString("foo=bar");
    Response response = mock(Response.class);
    String output = runRequest2(path, request, response);
    // Verify that the servlet never was run (there is no output).
    assertEquals("", output);
    // Verify that the request was redirected to the login url.
    String loginUrl =
        UserServiceFactory.getUserService()
            .createLoginURL(String.format("http://%s%s?foo=bar", SERVER_NAME + ":9999", path));
    verify(response).sendRedirect(loginUrl);
  }

  public void testUserRequired_WithUser() throws Exception {
    // Add a logged in user.
    helper.setEnvIsLoggedIn(true).setEnvEmail(USER_EMAIL).setEnvAuthDomain(TEST_ENV_DOMAIN).setUp();
    String path = "/user/blah";
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    assertEquals(String.format("%s: %s", USER_EMAIL, USER_EMAIL), output);
  }

  public void testAdminRequired_NonAdmin() throws Exception {
    // Add a logged in user.
    helper.setEnvIsLoggedIn(true).setEnvEmail(USER_EMAIL).setEnvAuthDomain(TEST_ENV_DOMAIN).setUp();
    String path = "/admin/blah";
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    // Verify that the servlet never was run (there is no output).
    assertEquals("", output);
    // Verify that the user got a 403 back.
    verify(response).sendError(SC_FORBIDDEN, "!role");
  }

  public void testAdminRequired_NoUser() throws Exception {
    String path = "/admin/blah";
    Request request = spy(new Request(null, null));
    //request.setServerPort(9999);
    HttpURI uri = new HttpURI("http", SERVER_NAME, 9999, path);
    HttpFields httpf = new HttpFields();
    MetaData.Request metadata = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, httpf);
    request.setMetaData(metadata);
    //  request.setAuthority(SERVER_NAME,9999);
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    // Verify that the servlet never was run (there is no output).
    assertEquals("", output);
    // Verify that the request was redirected to the login url.
    String loginUrl =
        UserServiceFactory.getUserService()
            .createLoginURL(String.format("http://%s%s", SERVER_NAME + ":9999", path));
    verify(response).sendRedirect(loginUrl);
  }

  public void testAdminRequired_WithAdmin() throws Exception {
    // Add a logged in admin.
    helper
        .setEnvIsLoggedIn(true)
        .setEnvIsAdmin(true)
        .setEnvEmail(ADMIN_EMAIL)
        .setEnvAuthDomain(TEST_ENV_DOMAIN)
        .setUp();
    String path = "/admin/blah";
    Request request = spy(new Request(null, null));
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    assertEquals(String.format("%s: %s", ADMIN_EMAIL, ADMIN_EMAIL), output);
  }

  public void testUserRequired_NoUserSkipAdminCheck() throws Exception {
    String path = "/user/blah";
    Request request = spy(new Request(null, null));
    doReturn("true").when(request).getAttribute("com.google.apphosting.internal.SkipAdminCheck");
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    assertEquals("null: null", output);
  }

  public void testAdminRequired_NoUserSkipAdminCheck() throws Exception {
    String path = "/admin/blah";
    Request request = spy(new Request(null, null));
    doReturn("true").when(request).getAttribute("com.google.apphosting.internal.SkipAdminCheck");
    Response response = mock(Response.class);
    String output = runRequest(path, request, response);
    assertEquals("null: null", output);
  }
}

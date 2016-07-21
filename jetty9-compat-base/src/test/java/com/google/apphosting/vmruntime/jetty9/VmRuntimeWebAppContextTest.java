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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Test VmRuntimeWebAppContext directly, without using VmRuntimeTestBase
 */
public class VmRuntimeWebAppContextTest {

  TestMetadataServer meta;
  private JettyRunner jetty;
  private Server server;
  private LocalConnector connector;
  private VmRuntimeWebAppContext context;

  @Before
  public void before() throws Exception {
    meta = new TestMetadataServer();
    meta.start();

    jetty = new JettyRunner("test-classes/webapp", -1);

    jetty.start();
    server = jetty.getServer();
    connector = (LocalConnector) server.getConnectors()[0];
    context =
        (VmRuntimeWebAppContext) server.getChildHandlersByClass(VmRuntimeWebAppContext.class)[0];
  }

  @After
  public void after() throws Exception {
    jetty.stop();
    meta.stop();
  }

  @Test
  public void testIsTrustedRemoteAddr() throws Exception {
    assertTrue(context.isTrustedRemoteAddr("127.0.0.1")); // Local host
    assertTrue(context.isTrustedRemoteAddr("172.17.42.1")); // Docker
    assertTrue(context.isTrustedRemoteAddr("169.254.160.2")); // Virtual Peers
    assertFalse(context.isTrustedRemoteAddr("130.211.0.100")); // GCLB
    assertFalse(context.isTrustedRemoteAddr("130.211.1.101")); // GCLB
    assertFalse(context.isTrustedRemoteAddr("130.211.2.102")); // GCLB
    assertFalse(context.isTrustedRemoteAddr("130.211.3.103")); // GCLB
    assertFalse(context.isTrustedRemoteAddr("10.0.0.3"));
    assertFalse(context.isTrustedRemoteAddr("123.123.123.123"));
    context.isDevMode = true;
    assertTrue(context.isTrustedRemoteAddr("123.123.123.123"));
  }

  @Test
  public void testNoRequestTicket() throws Exception {
    ReportProxyServlet servlet = new ReportProxyServlet();
    context.addServlet(new ServletHolder("test", servlet), "/*");
    String response = connector.getResponses("GET / HTTP/1.0\r\n\r\n");
    assertThat(response, Matchers.containsString("200 OK"));
    assertThat(
        response,
        Matchers.containsString(
            "initial ticket=google_com_test-project/testbackend.testversion.frontend1"));
    assertThat(
        response,
        Matchers.containsString(
            "committed ticket=google_com_test-project/testbackend.testversion.frontend1"));
    assertEquals(
        "google_com_test-project/testbackend.testversion.frontend1", servlet.getAfterClose());
  }

  @Test
  public void testRequestTicketClearNever() throws Exception {
    System.setProperty(VmApiProxyEnvironment.USE_GLOBAL_TICKET_KEY, "FALSE");
    VmRuntimeInterceptor.setDftClearTicket(VmRuntimeInterceptor.ClearTicket.NEVER);
    ReportProxyServlet servlet = new ReportProxyServlet();
    context.addServlet(new ServletHolder("test", servlet), "/*");
    String response =
        connector.getResponses(
            "GET / HTTP/1.0\r\n"
                + VmApiProxyEnvironment.TICKET_HEADER
                + ": request-ticket\r\n\r\n");
    assertThat(response, Matchers.containsString("200 OK"));
    assertThat(response, Matchers.containsString("initial ticket=request-ticket"));
    assertThat(response, Matchers.containsString("committed ticket=request-ticket"));
    assertEquals("request-ticket", servlet.getAfterClose());
  }

  @Test
  public void testRequestTicketClearOnCommit() throws Exception {
    System.setProperty(VmApiProxyEnvironment.USE_GLOBAL_TICKET_KEY, "FALSE");
    VmRuntimeInterceptor.setDftClearTicket(VmRuntimeInterceptor.ClearTicket.ON_COMMIT);
    ReportProxyServlet servlet = new ReportProxyServlet();
    context.addServlet(new ServletHolder("test", servlet), "/*");
    String response =
        connector.getResponses(
            "GET / HTTP/1.0\r\n"
                + VmApiProxyEnvironment.TICKET_HEADER
                + ": request-ticket\r\n\r\n");
    assertThat(response, Matchers.containsString("200 OK"));
    assertThat(response, Matchers.containsString("initial ticket=request-ticket"));
    assertThat(
        response,
        Matchers.containsString(
            "committed ticket=google_com_test-project/testbackend.testversion.frontend1"));
    assertEquals(
        "google_com_test-project/testbackend.testversion.frontend1", servlet.getAfterClose());
  }

  @Test
  public void testRequestTicketClearOnComplete() throws Exception {
    System.setProperty(VmApiProxyEnvironment.USE_GLOBAL_TICKET_KEY, "FALSE");
    VmRuntimeInterceptor.setDftClearTicket(VmRuntimeInterceptor.ClearTicket.ON_COMPLETE);
    ReportProxyServlet servlet = new ReportProxyServlet();
    context.addServlet(new ServletHolder("test", servlet), "/*");
    String response =
        connector.getResponses(
            "GET / HTTP/1.0\r\n"
                + VmApiProxyEnvironment.TICKET_HEADER
                + ": request-ticket\r\n\r\n");
    assertThat(response, Matchers.containsString("200 OK"));
    assertThat(response, Matchers.containsString("initial ticket=request-ticket"));
    assertThat(response, Matchers.containsString("committed ticket=request-ticket"));
    assertEquals(
        "google_com_test-project/testbackend.testversion.frontend1", servlet.getAfterClose());
  }

  @Test
  public void testRequestTicketUseGlobal() throws Exception {
    System.setProperty(VmApiProxyEnvironment.USE_GLOBAL_TICKET_KEY, "TRUE");
    ReportProxyServlet servlet = new ReportProxyServlet();
    context.addServlet(new ServletHolder("test", servlet), "/*");
    String response =
        connector.getResponses(
            "GET / HTTP/1.0\r\n"
                + VmApiProxyEnvironment.TICKET_HEADER
                + ": request-ticket\r\n\r\n");
    assertThat(response, Matchers.containsString("200 OK"));
    assertThat(
        response,
        Matchers.containsString(
            "initial ticket=google_com_test-project/testbackend.testversion.frontend1"));
    assertThat(
        response,
        Matchers.containsString(
            "committed ticket=google_com_test-project/testbackend.testversion.frontend1"));
    assertEquals(
        "google_com_test-project/testbackend.testversion.frontend1", servlet.getAfterClose());
  }

  private static class ReportProxyServlet extends HttpServlet {

    private String afterClose;

    public synchronized String getAfterClose() {
      return afterClose;
    }

    @Override
    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse response)
        throws ServletException, IOException {
      VmApiProxyEnvironment env = (VmApiProxyEnvironment) ApiProxy.getCurrentEnvironment();

      response.setContentType("text/plain;charset=iso-8859-1");
      PrintWriter writer = response.getWriter();

      writer.printf("initial ticket=%s%n", env.getTicket());
      response.flushBuffer();
      writer.printf("committed ticket=%s%n", env.getTicket());
      writer.close();
      afterClose = env.getTicket();
    }
  }
}

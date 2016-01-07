package com.google.apphosting.vmruntime.jetty9;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;

import org.junit.Assert;

@Ignore
public class TestAsyncContext {

  Server server = new Server();
  LocalConnector connector = new LocalConnector(server);
  VmRuntimeWebAppContext context = new VmRuntimeWebAppContext();
  
  @Before
  public void before() throws Exception {
    context.setResourceBase("src/test/resources/webapp");
    server.addConnector(connector);
    server.setHandler(context);
    
    context.setContextPath("/");
    context.addServlet(TestServlet.class, "/");
    context.init("WEB-INF/appengine-web.xml");
    
    server.start();
  }
  
  @After
  public void after() throws Exception {
    server.stop();
  }
  
  @Test
  public void testSimpleGet() throws Exception {
    String response=connector.getResponses(
        "GET / HTTP/1.0\r\n"+
        "\r\n");
    System.err.println("response="+response);
  }

  public static class TestServlet extends HttpServlet
  {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      
      Environment env = ApiProxy.getCurrentEnvironment();
      System.err.println(env);
      Assert.assertNotNull(env);
      super.doGet(req, resp);
    }
    
  }
}

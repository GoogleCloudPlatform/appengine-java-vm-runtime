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

package com.google.apphosting.vmruntime;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests the delegate for making AppEngine API calls in a Google Compute Engine VM.
 * Http call are using mock
 *
 */
public class VmApiProxyDelegateHttpCallTest
    extends AbstractVmApiProxyDelegateTest {


  Server server;

  byte[] response;

  int status;

  Throwable throwable;

  private HttpClient httpClient = new HttpClient( );

  @Override
  public void setUp() throws Exception {

    httpClient.start();

    server = new Server( 0 );
    ServletHandler servletHandler = new ServletHandler();
    server.setHandler( servletHandler );

    servletHandler.addServletWithMapping( new ServletHolder( new HttpServlet() {
        @Override
        protected void doPost( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException {
            resp.setStatus( status );
            resp.getOutputStream().write( response );
            if (throwable != null) {
              if (throwable.getClass() == ExecutionException.class ) {
                // here we have so simulate a timeout
                try {
                  Thread.sleep( SHORT_TIMEOUT + 5 );
                } catch ( InterruptedException e ) {
                  // no op
                }
              }
            }
        }
    } ), VmApiProxyDelegate.REQUEST_ENDPOINT );

    server.start();
  }

  @Override
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    httpClient.stop();
  }


  @Override
  protected HttpClient wrapRequest( byte[] expectedResponse, int expectedStatusCode,
                                    Throwable throwable )
    throws Exception {
    this.response = expectedResponse;
    this.status = expectedStatusCode;
    this.throwable = throwable;
    return httpClient;
  }


  @Override
  protected VmApiProxyEnvironment createEnvironment() {

    int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    Map<String,String> envMap = new HashMap<>(  );
    envMap.put( VmApiProxyEnvironment.MODULE_NAME_KEY, "app_foo_beer" );
    envMap.put( VmApiProxyEnvironment.LONG_APP_ID_KEY, "1");
    envMap.put( VmApiProxyEnvironment.PARTITION_KEY, "part");
    envMap.put( VmApiProxyEnvironment.VERSION_KEY, "1.0");
    envMap.put( VmApiProxyEnvironment.INSTANCE_KEY, "app");
    envMap.put( VmApiProxyEnvironment.TICKET_HEADER, TICKET );

    return VmApiProxyEnvironment.createDefaultContext( envMap, //
                                                       new VmMetadataCache(), //
                                                       "localhost:" + port,  //
                                                       new VmTimer(), //
                                                       Long.valueOf( 1000),
                                                       System.getProperty( "java.io.tmpdir" ) );
  }
}

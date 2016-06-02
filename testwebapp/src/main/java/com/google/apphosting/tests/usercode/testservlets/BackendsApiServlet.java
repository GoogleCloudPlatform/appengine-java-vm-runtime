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

package com.google.apphosting.tests.usercode.testservlets;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.BackendServiceFactory;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that prints out information from the Backends API
 *
 * <p/>If no parameters are specified the servlet will print out
 * &lt;current_instance&gt;.&lt;current_server&gt;
 *
 * <p/>If parameter "server" is specified the host:port for that server is printed
 * out.
 *
 * <p/>If both "server" and "instance" is specified the host:port for that instance
 * is printed out.
 *
 *
 */
public class BackendsApiServlet extends HttpServlet {
  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    BackendService backends = BackendServiceFactory.getBackendService();
    String server = req.getParameter("server");
    PrintWriter writer = res.getWriter();
    if (server == null) {
      String currentServerName = backends.getCurrentBackend();
      if (currentServerName == null) {
        currentServerName = "<default>";
      }
      int currentInstance = backends.getCurrentInstance();
      writer.append(currentInstance + "." + currentServerName + "\n");
    } else {
      String instance = req.getParameter("instance");
      if (instance != null) {
        writer.append(backends.getBackendAddress(server, Integer.parseInt(instance)) + "\n");
      } else {
        writer.append(backends.getBackendAddress(server) + "\n");
      }
    }
  }
}

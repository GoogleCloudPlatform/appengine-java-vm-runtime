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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.NetworkInterface;
import java.net.SocketException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet is used to test the exception-scrubbing logic in a special case.
 *
 * N.B. We use this servlet in java_sandbox_runtime_test even though the
 * code we are testing is not directly related to the sandbox. The reason is that
 * the code we are testing is sensitive to the exact structure of the call stack
 * and we wanted to simulate the callstack of a live runtime environment.
 *
 *
 */
public class ExceptionInInitServlet extends HttpServlet {

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      // This should throw a NullPointerException. If any other type of
      // exception is thrown then the init method will fail and the test will
      // fail. We are testing the exception-scrubbing logic in the case that an
      // exception
      // occurs via user code in a servlet init() method in a servlet that is in
      // a com.google...
      // package. Prior to CL 17131378 the following would have thrown an
      // ArrayIndexOutOfBoundsException.
      NetworkInterface.getByName(null);
    } catch (NullPointerException e) {
      // The correct type of exception was thrown. Now we want to exaine its
      // stack trace. If exception scrubbing succeeded then we should find
      // NetworkInterface
      // and not the mirror NetworkInterface_.
      StringWriter writer = new StringWriter();
      e.printStackTrace(new PrintWriter(writer));
      String str = writer.toString();
      if (str.contains("NetworkInterface_")) {
        throw new RuntimeException("Stack trace contains mirror class: " + str);
      }
      if (!str.contains("NetworkInterface")) {
        throw new RuntimeException("Stack trace does not contain expected class: " + str);
      }
    } catch (SocketException e) {
      throw new ServletException(e);
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setContentType("text/plain");
    response.getWriter().print("Success!");
  } // doGet
}

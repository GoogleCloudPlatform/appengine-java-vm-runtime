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

package com.google.apphosting.tests.usercode.testservlets.xxe;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Attempts XXE attacks on Jetty through web.xml. Theoretically we might be able
 * to attempt attacks on appengine-web.xml in the future here.
 */
public class XXEServlet extends HttpServlet {

  public void init(ServletConfig servletConfig) throws ServletException {
    testContents(servletConfig.getInitParameter("private-file"));
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.getWriter().println("OK");
  }

  private void testContents(String contents) throws ServletException {
    if (contents != null && contents.length() != 0) {
      throw new ServletException("Read a private file!\n" + contents);
    }
  }
}

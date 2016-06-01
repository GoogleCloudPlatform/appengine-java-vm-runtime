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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple servlet that only generates an exception and/or error status, based on query
 * parameters.
 *
 */
public class ErrorGenerationServlet extends HttpServlet {

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    String content = req.getParameter("content");
    if (content != null) {
      res.getWriter().println(content);
      if (req.getParameter("flush") != null) {
        res.getWriter().flush();
      }
    }

    String status = req.getParameter("status");
    if (status != null) {
      res.setStatus(new Integer(status));
    }
    String error = req.getParameter("error");
    if (error != null) {
      String msg = req.getParameter("msg");
      if (msg != null) {
        res.sendError(new Integer(error), msg);
      } else {
        res.sendError(new Integer(error));
      }
    }
    String ex = req.getParameter("exception");
    if (ex != null) {
      RuntimeException runtimeEx;
      try {
        runtimeEx = (RuntimeException) Class.forName(ex).newInstance();
      } catch (Exception rex) {
        throw new ServletException("can't instantiate " + ex, rex);
      }
      throw runtimeEx;
    }
  }
}

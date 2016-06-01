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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet for testing response header rewriting. */
public class ResponseHeadersServlet extends HttpServlet {
  @Override
  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Server", "iis");
    resp.setHeader("Date", "sometime in the summer");
    resp.setHeader("Content-Type", "text/html");
    resp.setHeader("Content-Length", "1500");
    resp.setHeader("Content-Encoding", "string");
    resp.setHeader("Accept-Encoding", "us-eng");
    resp.setHeader("Transfer-Encoding", "us-eng");
    // Not allowed: Non-ASCII header names.
    resp.setHeader("\u8bed", "Chinese");
    // Not allowed: Non-ASCII values.
    resp.setHeader("Content-Disposition", "attachment; filename=\"h\u00e9llo.png\"");
    resp.getWriter().print("this is my data");
  }
}

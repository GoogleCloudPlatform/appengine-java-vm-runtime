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
import java.io.InputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletContextResourceServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String path = req.getParameter("path");
    if (path == null) {
      path = req.getPathInfo();
    }

    InputStream inputStream = getServletContext().getResourceAsStream(path);
    if (inputStream == null) {
      throw new IOException("Could not load: " + path);
    }

    byte[] buffer = new byte[8192];
    int len;
    while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
      res.getOutputStream().write(buffer, 0, len);
    }
  }
}

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

import java.io.EOFException;
import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {
  /**
   * Static so we can test server reloading with a new ClassLoader.
   */
  private static int count = 0;

  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    doResponse(res);
  }

  public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    doResponse(res);

    int length = req.getIntHeader("Content-Length");
    char[] postData = new char[length];

    int pos = 0;
    while (pos < length) {
      int read = req.getReader().read(postData, pos, length - pos);
      if (read == -1) {
        throw new EOFException("expected " + (length - pos) + " more bytes");
      }
      pos += read;
    }

    res.getWriter().println("Post data is:" + new String(postData));
  }

  private void doResponse(HttpServletResponse res) throws IOException {
    res.setContentType("text/plain");
    res.setHeader("X-Test-Header", "test header");
    res.setHeader("X-Test-Count", String.valueOf(++count));
    res.getWriter().println("Hello, World!");
  }
}

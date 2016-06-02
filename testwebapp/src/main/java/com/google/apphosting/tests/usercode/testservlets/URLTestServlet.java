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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class URLTestServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    try {
      testURLInMap();
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    res.setContentType("text/plain");
    res.getWriter().println("PASS");
  }

  private void testURLInMap() throws Exception {
    Map<URL, String> map = new HashMap<URL, String>();
    map.put(new URL("http://google.com/foo"), "foo");
    map.put(new URL("http://google.com/bar"), "bar");
    map.put(new URL("http://foo.google.com"), "foo");

    map.get(new URL("http://foo.google.com/"));
    map.get(new URL("http://baz.google.com/"));
    map.get(new URL("http://google.com/baz"));
  }
}

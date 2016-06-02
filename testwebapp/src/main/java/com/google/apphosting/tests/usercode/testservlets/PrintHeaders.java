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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PrintHeaders extends HttpServlet {
  @SuppressWarnings("unchecked")
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    List<String> headers = new ArrayList<String>();

    Enumeration<String> headersEnum = req.getHeaderNames();
    while (headersEnum.hasMoreElements()) {
      headers.add(headersEnum.nextElement());
    }
    Collections.sort(headers);

    res.setContentType("text/plain");
    for (String header : headers) {
      res.getWriter().println(header + ": " + req.getHeader(header));
    }
  }
}

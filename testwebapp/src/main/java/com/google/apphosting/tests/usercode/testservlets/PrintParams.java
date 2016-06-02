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

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PrintParams extends HttpServlet {
  @SuppressWarnings("unchecked")
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    List<String> params = new ArrayList<String>();

    ServletConfig config = getServletConfig();

    Enumeration<String> paramsEnum = config.getInitParameterNames();
    while (paramsEnum.hasMoreElements()) {
      params.add(paramsEnum.nextElement());
    }
    Collections.sort(params);

    res.setContentType("text/plain");
    for (String param : params) {
      res.getWriter().println(param + ": " + config.getInitParameter(param));
    }
  }
}

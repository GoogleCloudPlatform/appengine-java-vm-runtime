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
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PrintQueryParams extends HttpServlet {
  @SuppressWarnings("unchecked")
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.setContentType("text/plain");
    Map<String, String[]> paramMap = (Map<String, String[]>) req.getParameterMap();
    for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
      for (String value : e.getValue()) {
        res.getWriter().print(e.getKey() + "=" + value);
      }
    }
  }
}

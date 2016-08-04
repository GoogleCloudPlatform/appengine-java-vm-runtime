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

import java.security.SecureRandom;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RandomServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    int randomCount = parseParameter(request.getParameter("randomCount"));
    int byteCount = parseParameter(request.getParameter("byteCount"));
    int seedByteCount = parseParameter(request.getParameter("seedByteCount"));

    for (int i = 0; i < randomCount; i++) {
      SecureRandom random = new SecureRandom();
      if (byteCount > 0) {
        random.nextBytes(new byte[byteCount]);
      }
      if (seedByteCount > 0) {
        random.generateSeed(seedByteCount);
      }
    }
  }

  private int parseParameter(String param) {
    if (param == null) {
      return 0;
    } else {
      return Integer.valueOf(param);
    }
  }
}

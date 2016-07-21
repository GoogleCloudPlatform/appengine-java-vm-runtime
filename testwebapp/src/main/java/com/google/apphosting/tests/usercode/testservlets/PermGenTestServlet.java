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
 * This servlet is invoked  in order to test that setting different
 * perm-gen sizes on the Java  works. We intern a bunch of big Strings in order to use up
 * perm-gen space. This code has been tested outside of App Engine to validate that it does use
 * approximately the amount of perm-gen space that it claims to use.
 *
 */
public class PermGenTestServlet extends HttpServlet {

  private static final int ONE_K = 1 << 10;
  private static final int ONE_HALF_K = 1 << 9;
  private static final int ONE_HALF_MEG = 1 << 19;
  // A String consuming about a MB of memory
  private static final String ONE_MEG_STRING;

  static {
    // Build a string with exactly one-half-meg of characters
    // and thus using approximately one MB of memory.
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < ONE_K; i++) {
      builder.append('x');
    }
    String oneKbString = builder.toString();
    builder = new StringBuilder();
    for (int i = 0; i < ONE_HALF_K; i++) {
      builder.append(oneKbString);
    }
    ONE_MEG_STRING = builder.toString();
    if (ONE_MEG_STRING.length() != ONE_HALF_MEG) {
      throw new RuntimeException("ONE_MEG_STRING.length()=" + ONE_MEG_STRING.length());
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/plain");
    int numMeg = Integer.parseInt(request.getParameter("mb"));
    usePermGenSpace(numMeg);
    response.getWriter().print("Success!");
  }

  /**
   * By interning numMegs Strings of size about one meg and holding on to a reference to the
   * interned strings, we put approximately numMegs megs into perm-gen space.
   */
  private static void usePermGenSpace(int numMegs) {
    String[] strings = new String[numMegs];
    for (int i = 0; i < numMegs; i++) {
      String str = ONE_MEG_STRING + i;
      strings[i] = str.intern();
    }
  }
}

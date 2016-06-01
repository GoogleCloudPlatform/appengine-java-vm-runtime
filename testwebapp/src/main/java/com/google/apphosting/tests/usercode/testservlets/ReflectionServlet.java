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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet tries to use reflection to access a private
 * Sun class which implements a public, whitelisted interface.
 *
 */
public class ReflectionServlet extends HttpServlet {

  public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    // This returns a private class, Collections$2.
    Enumeration<?> enumeration = Collections.enumeration(Collections.emptyList());
    try {
      // However, we should still be able to access the public
      // Enumeration methods reflectively.
      Method method = enumeration.getClass().getMethod("hasMoreElements");
      method.setAccessible(true);
      Object result = method.invoke(enumeration);
      res.getWriter().println("hasMoreElements = " + result);
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }
}

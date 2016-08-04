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
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that prints the classname of its own ClassLoader and the classname
 * of the context ClassLoader to the response.
 *
 */
public class ClassLoaderPrintingServlet extends HttpServlet {

  private static final String OUTPUT_FORMAT = "My ClassLoader: %s<br>Context ClassLoader: %s";

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    String output =
        String.format(
            OUTPUT_FORMAT,
            getClass().getClassLoader().getClass().getName(),
            Thread.currentThread().getContextClassLoader().getClass().getName());
    PrintWriter writer = httpServletResponse.getWriter();
    writer.print(output);
  }
}

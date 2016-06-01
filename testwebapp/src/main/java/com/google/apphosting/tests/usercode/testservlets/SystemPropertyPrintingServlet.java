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

import com.google.appengine.api.utils.SystemProperty;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that prints the values of some specific system properties.
 *
 */
public class SystemPropertyPrintingServlet extends HttpServlet {

  public static final String[] KEYS = {
    "sysprop1 key",
    "sysprop2 key",
    "sysprop3 key",
    SystemProperty.environment.key(),
    SystemProperty.version.key(),
    SystemProperty.applicationId.key(),
    SystemProperty.applicationVersion.key(),
  };

  public static final String[] BUILTIN_KEYS = {
    "java.class.version",
    "java.specification.name",
    "java.specification.vendor",
    "java.specification.version",
    "java.vendor",
    "java.vendor.url",
    "java.version",
    "java.vm.name",
    "java.vm.specification.name",
    "java.vm.specification.vendor",
    "java.vm.specification.version",
    "java.vm.vendor",
    "java.vm.version",
    "os.name"
  };

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    PrintWriter writer = httpServletResponse.getWriter();

    boolean builtin = Boolean.valueOf(httpServletRequest.getParameter("builtin"));

    if (builtin) {
      for (String key : BUILTIN_KEYS) {
        writer.println(key + " = " + System.getProperty(key));
      }
    } else {
      for (String key : KEYS) {
        writer.println(System.getProperty(key));
      }
    }
  }
}

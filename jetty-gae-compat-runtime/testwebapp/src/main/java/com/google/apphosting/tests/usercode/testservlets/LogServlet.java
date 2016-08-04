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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LogServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String message = req.getParameter("message");
    String context = req.getParameter("context");
    String levelName = req.getParameter("level");
    boolean exception = req.getParameter("exception") != null;

    String dest = req.getParameter("dest");
    if (dest.equals("stdout")) {
      System.out.println(message);
    } else if (dest.equals("stderr")) {
      System.err.println(message);
    } else if (dest.equals("logger")) {
      String multiply = req.getParameter("multiply");
      int multiplyCount = 1;
      if (multiply != null) {
        multiplyCount = Integer.parseInt(multiply);
      }
      // Optionally print the same message a large number of times (for testing large logs).
      for (int i = 0; i < multiplyCount; i++) {
        Logger log = Logger.getLogger(context);
        Level level = (levelName == null) ? Level.INFO : Level.parse(levelName);
        if (exception) {
          Logger.getLogger(context).log(level, message, new Exception(message));
        } else {
          Logger.getLogger(context).log(level, message);
        }
      }
    } else if (dest.equals("servlet")) {
      if (exception) {
        getServletContext().log(message, new Exception(message));
      } else {
        getServletContext().log(message);
      }
    } else if (dest.equals("global")) {
      // Logger.global should not be null anymore
      Logger.global.log(Level.INFO, message);
    } else {
      throw new ServletException("unknown destination: " + dest);
    }
  }
}

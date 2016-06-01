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
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LogSourceLocationServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    Logger logger = Logger.getLogger(LogSourceLocationServlet.class.getName());

    // Note: Do not change the line numbers below!
    logger.info("Info on line 20");

    try {
      Method method = Logger.class.getMethod("info", String.class);

      method.invoke(logger, "Info on line 25"); // Do not change
    } catch (NoSuchMethodException e) {
      logger.log(Level.SEVERE, "method lookup", e);
    } catch (IllegalAccessException e) {
      logger.log(Level.SEVERE, "invoke access", e);
    } catch (java.lang.reflect.InvocationTargetException e) {
      logger.log(Level.SEVERE, "invoke target", e);
    }
  }
}

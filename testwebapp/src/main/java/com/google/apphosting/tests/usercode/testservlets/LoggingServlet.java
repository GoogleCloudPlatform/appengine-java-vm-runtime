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

import static com.google.appengine.api.log.LogQuery.Builder.withIncludeAppLogs;

import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class LoggingServlet extends HttpServlet {
  private static volatile boolean configRan = false;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    getServletContext().log("LogTest Hello " + req.getQueryString());

    resp.setContentType("text/plain");
    if (req.getParameter("printLogs") != null) {
      LogService ls = LogServiceFactory.getLogService();
      LogQuery query = withIncludeAppLogs(true).minLogLevel(LogService.LogLevel.FATAL);
      for (RequestLogs logs : ls.fetch(query)) {
        for (AppLogLine logLine : logs.getAppLogLines()) {
          if (logLine.getLogLevel().equals(LogService.LogLevel.FATAL)) {
            resp.getWriter().println(logLine);
          }
        }
      }
    } else {
      Logger logger = Logger.getLogger("com.foo");
      resp.getWriter().println(logger.getLevel());
      Logger logger2 = Logger.getLogger("com.foo.bar");
      resp.getWriter().println(logger2.getLevel());
      resp.getWriter().println(configRan);
      logger2.severe("not null");
      logger2.severe((String) null);
    }
  }

  public static class Config {
    public Config() {
      configRan = true;
    }
  }
}

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

import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Retrieves the application's logs via the Logs API and displays them for the user.
 *
 */
public class LogViewerServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/plain");

    PrintWriter writer = resp.getWriter();

    boolean applogs = true;
    if ("false".equals(req.getParameter("applogs"))) {
      applogs = false;
    }

    boolean combined = false;
    if ("true".equals(req.getParameter("combined"))) {
      combined = true;
    }

    LogQuery query = LogQuery.Builder.withIncludeAppLogs(true).includeIncomplete(true);
    for (RequestLogs record : LogServiceFactory.getLogService().fetch(query)) {
      Calendar cal = Calendar.getInstance();
      cal.setTimeInMillis(record.getStartTimeUsec() / 1000);

      writer.println(String.format("\nDate: %s", cal.getTime().toString()));
      if (combined) {
        writer.println("COMBINED:" + record.getCombined());
      } else {
        writer.println("URL: " + record.getResource());
        writer.println("");
      }
      if (applogs) {
        List<AppLogLine> appLogs = record.getAppLogLines();
        for (AppLogLine appLog : appLogs) {
          writer.println("[" + appLog.getLogLevel() + "] " + appLog.getLogMessage());
        }
      }
    }
  }
}

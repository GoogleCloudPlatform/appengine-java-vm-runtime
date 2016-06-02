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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OpenFilesServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(OpenFilesServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    int numberOfFiles = Integer.valueOf(request.getParameter("num"));

    File file = new File(request.getRealPath("WEB-INF/web.xml"));
    List<InputStream> streams = new ArrayList<InputStream>(numberOfFiles);
    for (int i = 0; i < numberOfFiles; i++) {
      try {
        streams.add(new FileInputStream(file));
      } catch (FileNotFoundException ex) {
        logger.log(Level.WARNING, "Could not open " + file + " after " + streams.size(), ex);
        break;
      }
    }

    if (streams.size() != numberOfFiles) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } else {
      response.getWriter().println("Opened " + streams.size() + " FD's.");
    }

    for (InputStream is : streams) {
      is.close();
    }
  }
}

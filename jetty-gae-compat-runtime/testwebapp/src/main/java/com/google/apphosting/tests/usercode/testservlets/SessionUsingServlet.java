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
import java.io.NotSerializableException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 */
public class SessionUsingServlet extends HttpServlet {
  private static class NotSerializable {}

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession session = req.getSession();
    if (req.getParameter("key") != null) {
      session.setAttribute(req.getParameter("key"), req.getParameter("value"));
      setNonSerializableValues(session);
    }

    PrintWriter writer = resp.getWriter();
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain");
    Enumeration<?> names = session.getAttributeNames();
    if (names.hasMoreElements()) {
      while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        writer.println(name + ": " + session.getAttribute(name));
      }
    } else {
      writer.println("No session attributes defined.");
    }
  }

  private void setNonSerializableValues(HttpSession session) throws IOException {
    try {
      session.setAttribute("not serializable", new NotSerializable());
      throw new IOException("should not be able to add a non-serializable object!");
    } catch (RuntimeException e) {
      if (!(e.getCause() instanceof NotSerializableException)) {
        throw new IOException("wrong exception!", e.getCause());
      }
    }
  }
}

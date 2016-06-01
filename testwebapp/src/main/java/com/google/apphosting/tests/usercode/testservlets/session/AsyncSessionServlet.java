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

package com.google.apphosting.tests.usercode.testservlets.session;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * We'd prefer to make this an end-to-end test that writes an attribute to the
 * session and then reads it back out, but the session is backed by memcache,
 * the datastore, and, in the case of async sessions, task queue, and none of
 * these services are available in the JavaRuntimeTest.
 *
 * This test is currently useless, but I'm leaving it around because it won't be
 * useless forever.
 *
 */
public class AsyncSessionServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // TODO(xx): Uncomment once we get backend services running for
    // JavaRuntimeTest.
    //    request.getSession().setAttribute("timestamp", System.currentTimeMillis());
    resp.getWriter().print("success");
  }
}

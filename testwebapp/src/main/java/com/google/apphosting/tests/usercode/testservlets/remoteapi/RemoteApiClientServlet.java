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

package com.google.apphosting.tests.usercode.testservlets.remoteapi;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet used for testing the App Engine Remote API.  Verifies Datastore put/get/delete/query
 * operations using Keys with both the local app id and the remote app id.
 *
 */
public class RemoteApiClientServlet extends HttpServlet {

  private static final String LOCAL_APP_ID = "remoteapiclientapp";
  private static final String REMOTE_APP_ID = "helloworld-java-maxr";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int port = Integer.valueOf(req.getParameter("port"));

    RemoteApiSharedTests testHelper =
        new RemoteApiSharedTests.Builder()
            .setLocalAppId(LOCAL_APP_ID)
            .setRemoteAppId(REMOTE_APP_ID)
            .setUsername("test@google.com")
            .setPassword("password-not-used") // This is a DevAppServer, so password isn't enforced.
            .setServer("localhost")
            .setPort(port)
            .build();

    testHelper.runTests();

    resp.setContentType("text/plain");
    resp.getOutputStream().println("RemoteApiClientServlet OK");
  }
}

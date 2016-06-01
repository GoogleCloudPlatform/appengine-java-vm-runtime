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

import com.google.appengine.api.backends.BackendService;
//import com.google.appengine.tools.development.LocalEnvironment;
//import com.google.appengine.tools.development.ModulesController;
import com.google.apphosting.api.ApiProxy;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that verifies that the environment has the same keys in the
 * {@link Map} returned by {@link ApiProxy.Environment#getAttributes()}
 * during initialization as it does when servicing a request.  Exceptions are
 * allowed since some attributes only make sense in the context of a user
 * request.  This is an attempt to avoid future incidences of
 * bugs
 *
 */
public class LoadOnStartupServlet extends HttpServlet {
  // Keep this in sync with
  // com.google.appengine.tools.development.DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY. We do
  // import the Appengine internal definition here.
  static final String MODULES_FILTER_HELPER_PROPERTY =
      "com.google.appengine.tools.development.modules_filter_helper";

  private static final ApiProxy.Environment INIT_ENV = ApiProxy.getCurrentEnvironment();
  public static final String HTTP_SERVLET_REQUEST = "com.google.appengine.http_servlet_request";
  public static final String MODULES_CONTROLLER_ATTRIBUTE_KEY =
      "com.google.appengine.dev.modules_controller";

  /**
   * Attributes that don't need to be present in the init environment
   */
  private static final Set<String> REQUEST_ONLY_ATTRIBUTES =
      new HashSet<String>(
          Arrays.asList(
              // we don't limit the number of api requests during initialization -
              // technically incorrect but not a big deal
              "com.google.appengine.tools.development.api_call_semaphore",
              // no request so no user
              "com.google.appengine.api.users.UserService.user_id_key",
              // no request so no user org
              "com.google.appengine.api.users.UserService.user_organization",
              "com.google.appengine.api.files.filesapi_was_used",
              // backends API attributes in the devappserver
              BackendService.DEVAPPSERVER_PORTMAPPING_KEY,
              BackendService.INSTANCE_ID_ENV_ATTRIBUTE,
              BackendService.BACKEND_ID_ENV_ATTRIBUTE,
              // The HTTP Request is by definition only available during the request.
              HTTP_SERVLET_REQUEST,
              MODULES_CONTROLLER_ATTRIBUTE_KEY,
              MODULES_FILTER_HELPER_PROPERTY));

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    ApiProxy.Environment requestEnv = ApiProxy.getCurrentEnvironment();

    for (String key : requestEnv.getAttributes().keySet()) {
      if (!INIT_ENV.getAttributes().containsKey(key) && !REQUEST_ONLY_ATTRIBUTES.contains(key)) {
        resp.getWriter().println("Init environment attributes do not contain " + key);
      }
    }
  }
}

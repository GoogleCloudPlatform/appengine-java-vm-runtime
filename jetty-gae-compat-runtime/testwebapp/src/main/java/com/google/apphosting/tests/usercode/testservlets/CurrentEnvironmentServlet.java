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

import com.google.apphosting.api.ApiProxy;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that checks to see if a current environment has been set on the
 * ApiProxy.
 *
 */
public class CurrentEnvironmentServlet extends HttpServlet {
  private String appIdInInit;
  private String appModuleInInit;
  private String appVersionInInit;

  @Override
  public void init(ServletConfig servletConfig) throws ServletException {
    appIdInInit = ApiProxy.getCurrentEnvironment().getAppId();
    appModuleInInit = ApiProxy.getCurrentEnvironment().getModuleId();
    appVersionInInit = ApiProxy.getCurrentEnvironment().getVersionId();
  }

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    httpServletResponse.getWriter().println(appIdInInit);
    httpServletResponse.getWriter().println(appModuleInInit);
    httpServletResponse.getWriter().println(appVersionInInit);
  }
}

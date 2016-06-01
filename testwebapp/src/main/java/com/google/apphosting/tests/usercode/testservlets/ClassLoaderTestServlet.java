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
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that tests various aspects of the use of user-defined ClassLoaders in the Dev App
 * Server.
 * <p>
 * At the moment we test only one thing: That our byte code re-writing adds the proper stack-map
 * frames.
 *
 */
public class ClassLoaderTestServlet extends HttpServlet {

  private int foo(int x) {
    return 42;
  }

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    // This servlet is silly and pointless. I only
    // want to make sure that their are several jump targets
    // with stack map frames.
    try {
      if (foo(4) < 5) {
        System.out.println("foo");
      } else {
        ClassLoader cl = new URLClassLoader(new URL[] {(new File(".")).toURI().toURL()});
      }
    } catch (AccessControlException ace) {
      // expected
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

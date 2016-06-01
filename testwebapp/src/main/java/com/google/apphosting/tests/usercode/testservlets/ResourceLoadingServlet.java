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
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class ResourceLoadingServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    // load a resource with just user code on the stack
    writeToResponse("AnXmlResource.xml", response);

    // load a different resource with privileged code on the stack
    // the privileged code comes from the AccessController mirror
    AccessController.doPrivileged(
        new PrivilegedAction<Void>() {
          public Void run() {
            try {
              writeToResponse("AnotherXmlResource.xml", response);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            return null;
          }
        });
  }

  private void writeToResponse(String resourceName, HttpServletResponse response)
      throws IOException {
    URL url = getClass().getResource(resourceName);
    if (url == null) {
      throw new NullPointerException("Could not locate " + resourceName);
    }
    byte[] buffer = new byte[8192];
    int len;
    InputStream inputStream = url.openStream();
    while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
      response.getOutputStream().write(buffer, 0, len);
    }
  }
}

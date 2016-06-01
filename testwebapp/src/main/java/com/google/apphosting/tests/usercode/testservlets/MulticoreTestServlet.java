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
import java.util.concurrent.LinkedTransferQueue;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests various functions that read number of cores internally.
 * Copycat from other tests found nearby.
 *
 */
public class MulticoreTestServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/plain");
    boolean indirect = Boolean.parseBoolean(request.getParameter("indirect"));
    if (indirect) {
      response.getWriter().print(tryLinkedTransferQueue());
    } else {
      response.getWriter().print(Integer.toString(getCoresDirectlyTest()));
    }
  }

  private int getCoresDirectlyTest() {
    return Runtime.getRuntime().availableProcessors();
  }

  private String tryLinkedTransferQueue() {
    // This class is known to call native Runtime.availableProcessors() rather than the our
    // override.  Constructing is enough to call it.
    LinkedTransferQueue<String> queue = new LinkedTransferQueue<>();

    // Some classes are known to be loaded early, so their construction does not trigger security
    // violation because the sysconf() calls were made before sandboxing happenes.  These are
    // SynchronousQueue, ConcurrentHashMap, and possibly others.

    return "No syscall violation.";
  }
}

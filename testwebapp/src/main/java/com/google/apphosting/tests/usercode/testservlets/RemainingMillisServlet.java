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

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DeadlineExceededException;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet checks to see whether getRemainingMillis() decrements over time.
 *
 */
public class RemainingMillisServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    final long firstCall = ApiProxy.getCurrentEnvironment().getRemainingMillis();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while sleeping.");
    }
    final long secondCall = ApiProxy.getCurrentEnvironment().getRemainingMillis();
    if (secondCall >= firstCall) {
      throw new RuntimeException(
          "Time is not moving (or moving backwards): initial call = '"
              + firstCall
              + "', subsequent call = '"
              + secondCall
              + "'.");
    }

    res.setContentType("text/plain");
    res.getWriter()
        .println("You are the bread and the knife, " + "the crystal goblet and the wine.");

    // Unfortunately, if we're using this servlet to test the DevAppServer we can't continue
    // because it does not ever time out requests.
    if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Development) {
      return;
    }

    try {
      Thread.sleep(600000); //Sleep 10 minutes, this should guarantee an exception is thrown.
      throw new RuntimeException(
          "Expected the DeadlineExceededException to be thrown after" + "sleeping for 10mins");
    } catch (DeadlineExceededException e) {
      // There is an inherent race condition here that exists in the java runtime in general.
      // The problem that users run into here, is that they end up having to write code that
      // is gauranteed to complete in a specific amount of wall clock time.  In production,
      // this time is represented by a sleep that occurs in deadline_manager.cc and has little
      // to do with the java runtime itself.  Therefore, this segment of the test is always going
      // to be potentially flaky, we just try to do as much work before we get into this
      // exception handler as possible so that we don't run over our allocated cleanup time.
      if (ApiProxy.getCurrentEnvironment().getRemainingMillis() > 0) {
        throw new RuntimeException(
            "getRemainingMillis returned a result indicating there was time "
                + "remaining after DeadlineExceededException was thrown");
      }
      return;
    } catch (InterruptedException e) {
      throw new RuntimeException("The serving thread was interrupted before the deadline");
    }
  }
}

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

import com.google.apphosting.api.ApiBasePb.Integer32Proto;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DeadlineExceededException;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SleepServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String sleepParam = req.getParameter("time");

    int sleepMillis =
        (sleepParam != null) ? Integer.valueOf(sleepParam) : req.getIntHeader("Sleep-Time");

    String retryOnApiException = req.getHeader("Retry-On-Api-Exception");
    String retryOnSoftException = req.getHeader("Retry-On-Soft-Exception");
    String retryOnErrors = req.getHeader("Retry-On-Errors");
    String useBusyLoop = req.getHeader("Use-Busy-Loop");

    String useSleepApi = req.getHeader("Use-Sleep-Api");
    String useAsyncSleepApi = req.getHeader("Use-Async-Sleep-Api");
    String sleepApiMethod = req.getHeader("Sleep-Api-Method");
    if (sleepApiMethod == null) {
      sleepApiMethod = "Delay";
    }

    res.setContentType("text/plain");
    res.getWriter().println("Starting...");

    // This print is actually a hack so that the System_ mirror
    // initialization happens before we waste all of our time sleeping
    // and don't have any time left to clean up properly.
    //
    // TODO(xx): Should we be pre-initializing the mirrors
    // somehow so that this isn't even an issue?  It's likely to
    // confuse users if the first time they print or log is after a
    // DeadlineExceededException.
    System.err.println("About to sleep for " + sleepMillis);

    boolean keepGoing = true;
    while (keepGoing) {
      try {
        if (useBusyLoop != null) {
          long end = System.nanoTime() + sleepMillis * 1000000;
          while (System.nanoTime() < end) {
            for (int i = 0; i < 65535; i++) {
              // Busy wait
            }
          }
        } else if (useSleepApi != null) {
          Integer32Proto intRequest = new Integer32Proto();
          intRequest.setValue(sleepMillis);
          ApiProxy.makeSyncCall("google.util", sleepApiMethod, intRequest.toByteArray());
        } else if (useAsyncSleepApi != null) {
          Integer32Proto intRequest = new Integer32Proto();
          intRequest.setValue(sleepMillis);
          // Make an async call but ignore the response.
          ApiProxy.makeAsyncCall("google.util", sleepApiMethod, intRequest.toByteArray());
        } else {
          Thread.sleep(sleepMillis);
        }
        keepGoing = false;
      } catch (InterruptedException ex) {
        System.err.println("Caught: " + ex);
      } catch (ApiProxy.ApiDeadlineExceededException ex) {
        System.err.println("Caught: " + ex);
        if (retryOnApiException == null) {
          throw ex;
        }
      } catch (DeadlineExceededException ex) {
        System.err.println("Caught: " + ex);
        if (retryOnSoftException == null) {
          throw ex;
        }
      } catch (Error ex) {
        System.err.println("Caught: " + ex);
        if (retryOnErrors == null) {
          throw ex;
        }
      }
    }

    res.getWriter().println("Done.");
  }
}

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

package com.google.apphosting.tests.usercode.testservlets.security;

/**
 * {@link Restricted} gets repackaged into <code>com.google.appengine.repackaged</code>
 * at build time.  We run this code with the agent configured to throw exceptions
 * when it encounters references from usercode to anything in this package.
 *
 */
public class TestRepackagedAccess implements Runnable {

  // We're relying on consecutive, 1-based naming for anonymous inner classes.
  private static final String EXPECTED_ERROR_MSG_START =
      "Class " + TestRepackagedAccess.class.getName() + "$%d loaded from";

  @Override
  public void run() {
    try {
      // Each of these expected failures needs to be in its own class because we
      // only perform a single check for unsafe references per referer/referee
      // pair in each class.

      try {
        new Runnable() {
          public void run() {
            new Restricted();
            throw new RuntimeException("Expected a NoClassDefFoundError (1)");
          }
        }.run();
      } catch (NoClassDefFoundError e) {
        // good
        if (!e.getMessage().startsWith(String.format(EXPECTED_ERROR_MSG_START, 1))) {
          throw new RuntimeException("Wrong error message in exception: " + e.getMessage());
        }
      }

      try {
        new Runnable() {
          public void run() {
            Restricted.staticMethod();
            throw new RuntimeException("Expected a NoClassDefFoundError (2)");
          }
        }.run();
      } catch (NoClassDefFoundError e) {
        // good
        if (!e.getMessage().startsWith(String.format(EXPECTED_ERROR_MSG_START, 2))) {
          throw new RuntimeException("Wrong error message in exception: " + e.getMessage());
        }
      }

      try {
        new Runnable() {
          public void run() {
            Restricted.staticField = null;
            throw new RuntimeException("Expected a NoClassDefFoundError (3)");
          }
        }.run();
      } catch (NoClassDefFoundError e) {
        // good
        if (!e.getMessage().startsWith(String.format(EXPECTED_ERROR_MSG_START, 3))) {
          throw new RuntimeException("Wrong error message in exception: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

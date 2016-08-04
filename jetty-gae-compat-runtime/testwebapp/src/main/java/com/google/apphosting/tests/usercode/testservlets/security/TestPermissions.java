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

import java.lang.reflect.Field;

/**
 * Tests enforcement of permissions.
 *
 * @see com.google.apphosting.tests.usercode.testservlets.SecurityServlet
 */
public class TestPermissions implements Runnable {

  private static String privateFieldForTesting = "test";

  public void run() {
    // NB(tobyr) This may look like it's testing reflection, but it's really
    // testing that reflection permissions like accessDeclaredMembers and
    // suppressAccessChecks are inherited across ClassLoaders.

    try {
      Field field = getClass().getDeclaredField("privateFieldForTesting");

      // Test that setAccessible doesn't blow up
      field.setAccessible(true);

      // Reset to false to ensure we don't require unnecessary setAccessible
      // calls.
      field.setAccessible(false);

      String value = (String) field.get(null);
      if (!privateFieldForTesting.equals(value)) {
        throw new RuntimeException("Expected: " + privateFieldForTesting + " Actual: " + value);
      }

      field = String.class.getDeclaredField("value");

      try {
        field.get(privateFieldForTesting);
        throw new RuntimeException("Expected illegal access exception");
      } catch (IllegalAccessException e) {
        // Good
      }

      // TODO(xx) This would fail on the real runtime
      // See if there's any easy way to reproduce that behavior in dev_appserver
      field.setAccessible(true);

      // TODO(xx) Add a couple more tests in here.
      // I tried to add some for IO, but our current working directory is inaccurate
      // under blaze. I could correct for this, but it's not worth the effort, atm.
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

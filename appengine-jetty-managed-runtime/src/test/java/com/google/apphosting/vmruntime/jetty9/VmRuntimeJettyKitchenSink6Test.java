/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.apphosting.vmruntime.jetty9;

import java.util.Arrays;
import java.util.List;

/**
 * Misc individual Jetty9 vmengines tests.
 *
 */
public class VmRuntimeJettyKitchenSink6Test extends VmRuntimeTestBase {

  /**
   * Test that the thread local environment is set up on each request.
   *
   * @throws Exception
   */
  public void testEnvironmentInstall() throws Exception {
    String[] lines = fetchUrl(createUrl("/CurrentEnvironmentAccessor"));
    List<String> expectedLines = Arrays.asList(
            "testpartition~google.com:test-project",
            "testbackend",
            "testversion.0");
    assertEquals(expectedLines, Arrays.asList(lines));
  }

  /**
   * Test that the health check servlet was loaded and responds with "ok" with
   * the proper version provided.
   *
   * @throws Exception
   */
  public void testHealthOK() throws Exception {
    String[] lines = fetchUrl(createUrl("/_ah/health"));
    assertEquals(1, lines.length);
    assertEquals("ok", lines[0].trim());
  }
}

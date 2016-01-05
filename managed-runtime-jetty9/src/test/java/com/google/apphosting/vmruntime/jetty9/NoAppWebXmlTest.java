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
import static junit.framework.TestCase.assertTrue;

/**
 * Testing that appengine-web.xml can be optional.
 *
 */
public class NoAppWebXmlTest extends VmRuntimeTestBase {

  @Override
  protected void setUp() throws Exception {
    appengineWebXml = null;
    super.setUp();
  }

  /**
   * Tests that mapping a servlet to / works.
   *
   * @throws Exception
   */
  public void testWelcomeServlet() throws Exception {
    String[] lines = fetchUrl(createUrl("/"));
    assertTrue(Arrays.asList(lines).contains("Hello, World!"));
  }
}

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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;

import java.util.Arrays;

/**
 * Misc individual Jetty9 vmengines tests.
 *
 */
public class VmRuntimeJetty9KitchenSinkTest extends VmRuntimeTestBase {

  /**
   * Test that non compiled jsp files can be served.
   *
   * @throws Exception
   */
  public void testJspNotCompiled() throws Exception {
    int iter = 0;
    int end = 6;
    String[] lines =
        fetchUrl(createUrl(String.format("/hello_not_compiled.jsp?start=%d&end=%d", iter, end)));
    String iterationFormat = "<h2>Iteration %d</h2>";
    for (String line : lines) {
      System.out.println(line);
      if (!line.contains("Iteration")) {
        continue;
      }
      assertEquals(line.trim(), String.format(iterationFormat, iter++));
    }
    assertEquals(end + 1, iter);
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

  /**
   * Test that the API Proxy was configured by the VmRuntimeFilter.
   *
   * @throws Exception
   */
  public void testApiProxyInstall() throws Exception {
    assertNotNull(ApiProxy.getDelegate());
    assertEquals(VmApiProxyDelegate.class.getCanonicalName(),
        ApiProxy.getDelegate().getClass().getCanonicalName());
  }

}
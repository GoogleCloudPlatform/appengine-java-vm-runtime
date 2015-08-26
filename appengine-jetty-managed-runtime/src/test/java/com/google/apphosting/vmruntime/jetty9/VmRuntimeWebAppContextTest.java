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

/**
 * Tests for running AppEngine Java apps inside a VM using a Jetty 9 container.
 *
 * @author ludo@google.com
 */
public class VmRuntimeWebAppContextTest extends VmRuntimeTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    externalPort = 80;
  }

  public void testIsTrustedRemoteAddr() throws Exception {
    VmRuntimeWebAppContext vm = new VmRuntimeWebAppContext();
    assertTrue(vm.isTrustedRemoteAddr("127.0.0.1")); // Local host
    assertTrue(vm.isTrustedRemoteAddr("172.17.42.1")); // Docker
    assertTrue(vm.isTrustedRemoteAddr("169.254.160.2")); // Virtual Peers
    assertFalse(vm.isTrustedRemoteAddr("130.211.0.100")); // GCLB
    assertFalse(vm.isTrustedRemoteAddr("130.211.1.101")); // GCLB
    assertFalse(vm.isTrustedRemoteAddr("130.211.2.102")); // GCLB
    assertFalse(vm.isTrustedRemoteAddr("130.211.3.103")); // GCLB
    assertFalse(vm.isTrustedRemoteAddr("10.0.0.3"));
    assertFalse(vm.isTrustedRemoteAddr("123.123.123.123"));
    vm.isDevMode = true;
    assertTrue(vm.isTrustedRemoteAddr("123.123.123.123"));
  }

}

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

package com.google.apphosting.vmruntime;

/**
 * VM request utils.
 */
public class VmRequestUtils {
  public static final String LINK_LOCAL_IP_NETWORK = "169.254";

  /**
   * Checks if a remote address is trusted for the purposes of handling requests.
   *
   * @param remoteAddr String representation of the remote ip address.
   * @return True if and only if the remote address should be allowed to make requests.
   */
  public static boolean isTrustedRemoteAddr(boolean isDevMode, String remoteAddr) {
    if (isDevMode) {
      return isDevMode;
    } else if (remoteAddr == null) {
      return false;
    } else if (remoteAddr.startsWith("172.17.")) {
      // Allow traffic from default docker ip bride range (172.17.0.0/16)
      return true;
    } else if (remoteAddr.startsWith(LINK_LOCAL_IP_NETWORK)) {
      return true;
    } else if (remoteAddr.equals("127.0.0.2")) {
      // Test untrusted.
      return false;
    } else if (remoteAddr.startsWith("127.0.0.")) {
      // Allow localhost.
      return true;
    }
    return false;
  }
}

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

package com.google.apphosting.vmruntime.jetty9;

import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

/**
 * Simple class holding information about a single API request.
 */
public class ApiRequest {
  public final VmApiProxyEnvironment requestEnvironment;
  public final String packageName;
  public final String methodName;
  public final byte[] requestData;

  /**
   * Simple API request.
   *
   * @param environment the environment
   * @param packageName the package name
   * @param methodName the method name
   * @param requestData the request data
     */
  public ApiRequest(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData) {
    this.requestEnvironment = environment;
    this.packageName = packageName;
    this.methodName = methodName;
    this.requestData = requestData;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getMethodName() {
    return methodName;
  }

  public byte[] getRequestData() {
    return requestData;
  }

  protected boolean waitForAllApiCalls() {
    // Only for testing logging. The runtime should wait for all other API calls automatically.
    return requestEnvironment.waitForAllApiCallsToComplete(1000);
  }
}

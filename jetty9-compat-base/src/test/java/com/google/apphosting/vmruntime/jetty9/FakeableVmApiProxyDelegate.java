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

import com.google.appengine.repackaged.com.google.protobuf.MessageLite;
import com.google.apphosting.api.ApiBasePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.vmruntime.VmApiProxyDelegate;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import java.util.LinkedList;

/**
 * ApiProxyDelegate that stores all incoming API requests and returns
 * pre-configured responses.
 */
public class FakeableVmApiProxyDelegate extends VmApiProxyDelegate {

  public LinkedList<MessageLite> responses = new LinkedList<>();
  public LinkedList<ApiRequest> requests = new LinkedList<>();
  public boolean ignoreLogging = true;

  public FakeableVmApiProxyDelegate() {}

  @Override
  protected byte[] runSyncCall(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData,
      int timeoutMs)
      throws ApiProxy.ApiProxyException {
    // Lots of tests triggers logging. Ignore calls to the logservice by default. Tests
    // verifying logging behavior can enable the log api capture calling setIgnoreLogging(false).
    if (ignoreLogging && "logservice".equals(packageName)) {
      return new ApiBasePb.VoidProto().toByteArray();
    }
    if ("google.util".equals(packageName) && "Delay".equals(methodName)) {
      return handleDelayApi(requestData);
    }
    requests.add(new ApiRequest(environment, packageName, methodName, requestData));
    if (responses.isEmpty()) {
      throw new RuntimeException(
          "Got unexpected ApiProxy call to: " + packageName + "/" + methodName);
    }
    return responses.removeFirst().toByteArray();
  }

  public void setIgnoreLogging(boolean ignore) {
    this.ignoreLogging = ignore;
  }

  /**
   * Add a API response to return on the next API call.
   *
   * @param message The protocol buffer to return.
   */
  public void addApiResponse(MessageLite message) {
    responses.add(message);
  }

  /**
   * Returns the last API request made through the delegate.
   *
   * @return A serialized protocol buffer.
   */
  public ApiRequest getLastRequest() {
    return requests.getLast();
  }

  private byte[] handleDelayApi(byte[] requestData) {
    ApiBasePb.Integer32Proto intRequest = new ApiBasePb.Integer32Proto();
    intRequest.parseFrom(requestData);
    try {
      Thread.sleep(intRequest.getValue());
    } catch (InterruptedException e) {
      throw new ApiProxy.ApiProxyException("Got unexpected thread interrupt!");
    }
    return new ApiBasePb.VoidProto().toByteArray();
  }

  public boolean waitForLogFlush() throws InterruptedException {
    for (int i = 0; i < 10; i++) {
      if (requests.size() > 0 && "logservice".equals(requests.getLast().packageName)) {
        return requests.getLast().waitForAllApiCalls();
      }
      Thread.sleep(100);
    }
    return false;
  }
}

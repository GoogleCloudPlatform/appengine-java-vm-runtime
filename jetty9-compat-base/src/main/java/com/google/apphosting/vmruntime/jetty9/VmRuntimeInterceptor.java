/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS-IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.apphosting.vmruntime.jetty9;

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;

/**
 * HttpOutput Interceptor.
 *
 * <p>The interceptor is configured via beta settings from the appengine-web.xml file,
 * with the following keys and values are:</p>
 * <dl>
 *   <dt>clear-api-ticket-on</dt><dd>When to clear the API ticket from the VmApiProxyEnvironment: NEVER (default), COMMIT, or LAST</dd>
 * </dl>
 */
class VmRuntimeInterceptor implements HttpOutput.Interceptor {

  private static ClearTicket dftClearTicket=ClearTicket.NEVER;

  private final VmApiProxyEnvironment environment;
  private final HttpOutput.Interceptor next;
  private final ClearTicket clearTicket;

  public static ClearTicket getDftClearTicket() {
    return dftClearTicket;
  }

  public static void setDftClearTicket(ClearTicket dftClearTicket) {
    VmRuntimeInterceptor.dftClearTicket = dftClearTicket;
  }

  public static void init(AppEngineWebXml appEngineWebXml) {
    String s=appEngineWebXml.getBetaSettings().get("clear-api-ticket-on");
    if (s!=null) {
      switch (s.toLowerCase()) {
        case "commit":
          dftClearTicket=ClearTicket.ON_COMMIT;
          break;
        case "last":
          dftClearTicket=ClearTicket.ON_LAST;
          break;
      }
    }
  }

  public enum ClearTicket { NEVER, ON_COMMIT, ON_LAST }

  public VmRuntimeInterceptor(VmApiProxyEnvironment environment, HttpOutput.Interceptor next) {
    this.environment = environment;
    this.next = next;
    this.clearTicket = dftClearTicket;
  }

  @Override
  public void write(ByteBuffer content, boolean last, Callback callback) {
    if (clearTicket == ClearTicket.ON_COMMIT)
      environment.clearTicket();

    next.write(content, last, callback);

    if (clearTicket == ClearTicket.ON_LAST && last)
      environment.clearTicket();
  }

  @Override
  public boolean isOptimizedForDirectBuffers() {
    return next.isOptimizedForDirectBuffers();
  }

  @Override
  public HttpOutput.Interceptor getNextInterceptor() {
    return next;
  }
}

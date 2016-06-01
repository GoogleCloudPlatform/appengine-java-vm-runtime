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

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;

/**
 * HttpOutput Interceptor. Used to call {@link VmApiProxyEnvironment#clearTicket()} either on:<dl>
 * <dt>NEVER</dt><dd>The request ticket is never cleared</dd> <dt>ON_COMMIT</dt><dd>The request
 * ticket is cleared when he response is committed</dd> <dt>ON_COMPLETE</dt><dd>The request ticket
 * is cleared when he response content is complete</dd> </dl> The default behavior is ON_COMPLETE,
 * but can be set with the System Property or Environment variable {@link
 * VmApiProxyEnvironment#CLEAR_REQUEST_TICKET_KEY}
 */
class VmRuntimeInterceptor implements HttpOutput.Interceptor {

  public enum ClearTicket {
    NEVER,
    ON_COMMIT,
    ON_COMPLETE
  }

  private static ClearTicket dftClearTicket = ClearTicket.ON_COMPLETE;

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
    String clearTicket =
        VmApiProxyEnvironment.getSystemPropertyOrEnv(
            System.getenv(),
            VmApiProxyEnvironment.CLEAR_REQUEST_TICKET_KEY,
            ClearTicket.ON_COMPLETE.name());
    dftClearTicket = ClearTicket.valueOf(clearTicket.toUpperCase());
  }

  public VmRuntimeInterceptor(VmApiProxyEnvironment environment, HttpOutput.Interceptor next) {
    this.environment = environment;
    this.next = next;
    this.clearTicket = dftClearTicket;
  }

  @Override
  public void write(ByteBuffer content, boolean last, Callback callback) {
    if (clearTicket == ClearTicket.ON_COMMIT) {
      environment.clearTicket();
    }

    next.write(content, last, callback);

    if (clearTicket == ClearTicket.ON_COMPLETE && last) {
      environment.clearTicket();
    }
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

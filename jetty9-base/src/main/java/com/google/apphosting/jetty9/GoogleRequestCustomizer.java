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

package com.google.apphosting.jetty9;

import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.annotation.Name;

import java.net.InetSocketAddress;

public class GoogleRequestCustomizer implements HttpConfiguration.Customizer {

  public static final String HTTPS_HEADER = "X-AppEngine-Https";
  public static final String USERIP_HEADER = "X-AppEngine-User-IP";

  private final int httpPort;
  private final int httpsPort;

  public GoogleRequestCustomizer() {
    this(80, 443);
  }

  public GoogleRequestCustomizer(@Name("httpPort") int httpPort, @Name("httpsPort") int httpsPort) {
    super();
    this.httpPort = httpPort;
    this.httpsPort = httpsPort;
  }

  @Override
  public void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
    String https = request.getHeader(HTTPS_HEADER);
    if ("on".equals(https)) {
      request.setSecure(true);
      request.setScheme(HttpScheme.HTTPS.toString());
      request.setAuthority(request.getServerName(), httpsPort);
    } else if ("off".equals(https)) {
      request.setSecure(false);
      request.setScheme(HttpScheme.HTTP.toString());
      request.setAuthority(request.getServerName(), httpPort);
    }

    String userip = request.getHeader(USERIP_HEADER);
    if (userip != null) {
      request.setRemoteAddr(InetSocketAddress.createUnresolved(userip, request.getRemotePort()));
    }
  }
}

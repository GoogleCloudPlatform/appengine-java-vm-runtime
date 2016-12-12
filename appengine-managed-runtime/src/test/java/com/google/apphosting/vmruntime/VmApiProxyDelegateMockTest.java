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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Tests the delegate for making AppEngine API calls in a Google Compute Engine VM.
 * Http call are using mock
 *
 */
public class VmApiProxyDelegateMockTest extends AbstractVmApiProxyDelegateTest {

  protected VmApiProxyEnvironment createEnvironment() {
    VmApiProxyEnvironment environment = mock(VmApiProxyEnvironment.class);
    when(environment.getTicket()).thenReturn(TICKET);

    Map<String, Object> attributes = new HashMap<>();
    when(environment.getAttributes()).thenReturn(attributes);
    return environment;
  }

  private HttpClient createMockHttpClient() {
    HttpClient httpClient = mock( HttpClient.class);
    return httpClient;
  }

  private Request createMockRequest() {
    Request request = mock(Request.class);
    return request;
  }

  private ContentResponse createMockHttpResponse( byte[] response, int code)
    throws IllegalStateException, IOException {

    ContentResponse resp = mock(ContentResponse.class);

    when(resp.getContent()).thenReturn(response);
    when(resp.getStatus()).thenReturn(code);
    return resp;
  }

  @Override
  protected HttpClient wrapRequest( byte[] expectedResponse, int expectedStatusCode,
                                    Throwable throwable )
    throws Exception {
    HttpClient mockClient = createMockHttpClient();
    ContentResponse mockHttpResponse = createMockHttpResponse(expectedResponse, expectedStatusCode);

    Request request = createMockRequest();

    when(mockClient.POST(Mockito.any(String.class)))
        .thenReturn(request);

    if (throwable != null) {
      when( request.send() ).thenThrow( throwable );
    } else {
      when( request.send() ).thenReturn( mockHttpResponse );
    }
    return mockClient;
  }
}

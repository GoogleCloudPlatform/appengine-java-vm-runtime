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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.log.LogServiceException;
import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.repackaged.com.google.common.collect.ImmutableMap;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.utils.remoteapi.RemoteApiPb;

import junit.framework.TestCase;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.mockito.Mockito;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tests the delegate for making AppEngine API calls in a Google Compute Engine VM.
 *
 */
public class VmApiProxyDelegateTest extends TestCase {
  private static final String TICKET = "test-ticket";
  public static final String TEST_PACKAGE_NAME = "test package";
  public static final String TEST_METHOD_NAME = "test method";
  public static final String TEST_ERROR_MESSAGE = "test error message";
  public static final int TEST_APPLICATION_ERROR = 505;

  public static Map<RemoteApiPb.RpcError.ErrorCode, ApiProxyException> createErrorToExceptionMap() {
    return new ImmutableMap.Builder<RemoteApiPb.RpcError.ErrorCode, ApiProxyException>()
        .put(
            RemoteApiPb.RpcError.ErrorCode.UNKNOWN,
            new ApiProxy.UnknownException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.CALL_NOT_FOUND,
            new ApiProxy.CallNotFoundException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.PARSE_ERROR,
            new ApiProxy.ArgumentException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.SECURITY_VIOLATION,
            new ApiProxy.UnknownException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.OVER_QUOTA,
            new ApiProxy.OverQuotaException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.REQUEST_TOO_LARGE,
            new ApiProxy.RequestTooLargeException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.CAPABILITY_DISABLED,
            new ApiProxy.CapabilityDisabledException(
                TEST_ERROR_MESSAGE, TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.FEATURE_DISABLED,
            new ApiProxy.FeatureNotEnabledException(
                TEST_ERROR_MESSAGE, TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.BAD_REQUEST,
            new ApiProxy.ArgumentException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.RESPONSE_TOO_LARGE,
            new ApiProxy.ResponseTooLargeException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.CANCELLED,
            new ApiProxy.CancelledException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.REPLAY_ERROR,
            new ApiProxy.UnknownException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .put(
            RemoteApiPb.RpcError.ErrorCode.DEADLINE_EXCEEDED,
            new ApiProxy.ApiDeadlineExceededException(TEST_PACKAGE_NAME, TEST_METHOD_NAME))
        .build();
  }

  private HttpClient createMockHttpClient() {
    HttpClient httpClient = mock( HttpClient.class);
    //when(httpClient.getConnectionManager()).thenReturn(new PoolingClientConnectionManager());
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

  private VmApiProxyEnvironment createMockEnvironment() {
    VmApiProxyEnvironment environment = mock(VmApiProxyEnvironment.class);
    when(environment.getTicket()).thenReturn(TICKET);

    Map<String, Object> attributes = new HashMap<>();
    when(environment.getAttributes()).thenReturn(attributes);
    return environment;
  }

  private void callDelegateWithSuccess(boolean sync) throws Exception {
    RemoteApiPb.Response response = new RemoteApiPb.Response();
    byte[] pbData = new byte[] {0, 1, 2, 3, 4, 5};
    response.setResponseAsBytes(pbData);

    HttpClient mockClient = createMockHttpClient();
    ContentResponse mockHttpResponse =
        createMockHttpResponse(response.toByteArray(), HttpURLConnection.HTTP_OK);

    Request request = createMockRequest();

    when(mockClient.POST(Mockito.any(String.class)))
        .thenReturn(request);

    when( request.send() ).thenReturn( mockHttpResponse );

    VmApiProxyDelegate delegate = new VmApiProxyDelegate(mockClient);
    VmApiProxyEnvironment environment = createMockEnvironment();

    final Double timeoutInSeconds = 3.0;
    byte[] result = null;
    if (sync) {
      environment.getAttributes().put(VmApiProxyDelegate.API_DEADLINE_KEY, timeoutInSeconds);
      result = delegate.makeSyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, pbData);
    } else {
      ApiConfig apiConfig = new ApiConfig();
      apiConfig.setDeadlineInSeconds(timeoutInSeconds);
      result =
          delegate
              .makeAsyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, pbData, apiConfig)
              .get();
    }
    assertTrue(Arrays.equals(pbData, result));
  }

  private void callDelegateWithOneError(
      boolean sync,
      RemoteApiPb.RpcError rpcError,
      RemoteApiPb.ApplicationError appError,
      ApiProxyException expectedException)
      throws Exception {
    // Create the response for the mock connection.
    RemoteApiPb.Response response = new RemoteApiPb.Response();
    if (appError != null) {
      response.setApplicationError(appError);
    }
    if (rpcError != null) {
      response.setRpcError(rpcError);
    }

    HttpClient mockClient = createMockHttpClient();
    ContentResponse mockHttpResponse =
        createMockHttpResponse(response.toByteArray(), HttpURLConnection.HTTP_OK);
    Request request = createMockRequest();
    when(mockClient.POST(Mockito.any(String.class)))
        .thenReturn(request);

    when( request.send() ).thenReturn( mockHttpResponse );

    VmApiProxyDelegate delegate = new VmApiProxyDelegate(mockClient);
    VmApiProxyEnvironment environment = createMockEnvironment();

    byte[] requestData = new byte[] {0, 1, 2, 3, 4, 5};
    byte[] result = null;
    final Double timeoutInSeconds = 10.0;

    try (SuppressedLogging l = new SuppressedLogging()) {
      if (sync) {
        try {
          environment.getAttributes().put(VmApiProxyDelegate.API_DEADLINE_KEY, timeoutInSeconds);
          result =
              delegate.makeSyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData);
          fail();
        } catch (ApiProxyException exception) {
          assertEquals(expectedException.getClass(), exception.getClass());
        }
      } else {
        try {
          ApiConfig apiConfig = new ApiConfig();
          apiConfig.setDeadlineInSeconds(timeoutInSeconds);
          result =
              delegate
                  .makeAsyncCall(
                      environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData, apiConfig)
                  .get();
          fail();
        } catch (ExecutionException exception) {
          // ExecutionException is expected, and make sure the cause is expected as well.
          assertEquals(expectedException.getClass(), exception.getCause().getClass());
        }
      }
    }
    assertNull(result);
  }

  private void callDelegateWithHttpError(boolean sync, ApiProxyException expectedException)
      throws Exception {
    HttpClient mockClient = createMockHttpClient();
    ContentResponse mockHttpResponse = createMockHttpResponse("Error from RPC proxy".getBytes(),
        HttpURLConnection.HTTP_UNAVAILABLE);
    Request request = createMockRequest();
    when(mockClient.POST(Mockito.any(String.class)))
        .thenReturn(request);
    when( request.send() ).thenReturn( mockHttpResponse );

    VmApiProxyDelegate delegate = new VmApiProxyDelegate(mockClient);
    VmApiProxyEnvironment environment = createMockEnvironment();

    byte[] requestData = new byte[] {0, 1, 2, 3, 4, 5};
    byte[] result = null;
    final Double timeoutInSeconds = 10.0;

    try (SuppressedLogging l = new SuppressedLogging()) {
      if (sync) {
        try {
          environment.getAttributes().put(VmApiProxyDelegate.API_DEADLINE_KEY, timeoutInSeconds);
          result =
              delegate.makeSyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData);
          fail();
        } catch (ApiProxyException exception) {
          assertThat(exception, instanceOf(expectedException.getClass()));
        }
      } else {
        try {
          ApiConfig apiConfig = new ApiConfig();
          apiConfig.setDeadlineInSeconds(timeoutInSeconds);
          result =
              delegate
                  .makeAsyncCall(
                      environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData, apiConfig)
                  .get();
          fail();
        } catch (ExecutionException exception) {
          // ExecutionException is expected, and make sure the cause is expected as well.
          assertThat(exception.getCause(), instanceOf(expectedException.getClass()));
        }
      }
    }
    assertNull(result);
  }

  private void callDelegateWithConnectionError(boolean sync, ApiProxyException expectedException)
      throws Exception {
    HttpClient mockClient = createMockHttpClient();

    Request request = createMockRequest();
    createMockHttpResponse(new byte[0], HttpURLConnection.HTTP_UNAVAILABLE);
    when(mockClient.POST(Mockito.any(String.class))).thenReturn( request );
    when(request.send()).thenThrow(new ExecutionException("Connection refused", new Throwable(  )));

    VmApiProxyDelegate delegate = new VmApiProxyDelegate(mockClient);
    VmApiProxyEnvironment environment = createMockEnvironment();

    byte[] requestData = new byte[] {0, 1, 2, 3, 4, 5};
    byte[] result = null;
    final Double timeoutInSeconds = 10.0;

    try (SuppressedLogging l = new SuppressedLogging()) {
      if (sync) {
        try {
          environment.getAttributes().put(VmApiProxyDelegate.API_DEADLINE_KEY, timeoutInSeconds);
          result =
              delegate.makeSyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData);
          fail();
        } catch (ApiProxyException exception) {
          assertEquals(expectedException.getClass(), exception.getClass());
        }
      } else {
        try {
          ApiConfig apiConfig = new ApiConfig();
          apiConfig.setDeadlineInSeconds(timeoutInSeconds);
          result =
              delegate
                  .makeAsyncCall(
                      environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData, apiConfig)
                  .get();
          fail();
        } catch (ExecutionException exception) {
          // ExecutionException is expected, and make sure the cause is expected as well.
          assertEquals(expectedException.getClass(), exception.getCause().getClass());
        }
      }
    }
    assertNull(result);
  }

  private void callDelegateWithParsingError(boolean sync, ApiProxyException expectedException)
      throws Exception {
    // Create the response for the mock connection.
    byte[] brokenResponse = new byte[] {47, 11, 17, 32};
    HttpClient mockClient = createMockHttpClient();
    ContentResponse mockHttpResponse =
        createMockHttpResponse(brokenResponse, HttpURLConnection.HTTP_OK);
    Request request = createMockRequest();
    when(mockClient.POST(Mockito.any(String.class)))
        .thenReturn(request);
    when( request.send() ).thenReturn( mockHttpResponse );


    VmApiProxyDelegate delegate = new VmApiProxyDelegate(mockClient);
    VmApiProxyEnvironment environment = createMockEnvironment();

    byte[] requestData = new byte[] {0, 1, 2, 3, 4, 5};
    byte[] result = null;

    try (SuppressedLogging l = new SuppressedLogging()) {
      if (sync) {
        try {
          result =
              delegate.makeSyncCall(environment, TEST_PACKAGE_NAME, TEST_METHOD_NAME, requestData);
          fail();
        } catch (ApiProxyException exception) {
          assertEquals(expectedException.getClass(), exception.getClass());
        }
      } else {
        try {
          result =
              delegate
                  .makeAsyncCall(
                      environment,
                      TEST_PACKAGE_NAME,
                      TEST_METHOD_NAME,
                      requestData,
                      new ApiConfig())
                  .get();
          fail();
        } catch (ExecutionException exception) {
          // ExecutionException is expected, and make sure the cause is expected as well.
          assertEquals(expectedException.getClass(), exception.getCause().getClass());
        }
      }
    }
    assertNull(result);
  }

  private void callDelegateWithAllErrors(boolean sync) throws Exception {
    Map<RemoteApiPb.RpcError.ErrorCode, ApiProxyException> rmtResponseRpcErrorToExceptionMap =
        createErrorToExceptionMap();
    for (Map.Entry<RemoteApiPb.RpcError.ErrorCode, ApiProxyException> entry :
        rmtResponseRpcErrorToExceptionMap.entrySet()) {
      RemoteApiPb.RpcError rpcError = new RemoteApiPb.RpcError();
      rpcError.setCode(entry.getKey().ordinal());
      ApiProxyException expectedException = entry.getValue();
      callDelegateWithOneError(sync, rpcError, null, expectedException);
    }

    RemoteApiPb.ApplicationError appError = new RemoteApiPb.ApplicationError();
    appError.setCode(13);
    appError.setDetail("blah");
    ApiProxyException expectedException =
        new ApiProxy.ApplicationException(TEST_APPLICATION_ERROR, TEST_ERROR_MESSAGE);
    callDelegateWithOneError(sync, null, appError, expectedException);
  }

  /*
  public void testConstructorAndConnectionMonitorThread() throws Exception {
    VmApiProxyDelegate delegate = new VmApiProxyDelegate();
    delegate.monitorThread.interrupt();
    delegate.monitorThread.join();
  }*/

  public void testMakeSyncCall_Success() throws Exception {
    callDelegateWithSuccess(true);
  }

  public void testMakeAsyncCall_Success() throws Exception {
    callDelegateWithSuccess(false);
  }

  public void testMakeSyncCall_HttpError() throws Exception {
    callDelegateWithHttpError(
        true, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeAsyncCall_HttpError() throws Exception {
    callDelegateWithHttpError(
        false, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeSyncCall_ConnectionError() throws Exception {
    callDelegateWithConnectionError(
        true, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeAsyncCall_ConnectionError() throws Exception {
    callDelegateWithConnectionError(
        false, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeSyncCall_ParsingError() throws Exception {
    callDelegateWithParsingError(
        true, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeAsyncCall_ParsingError() throws Exception {
    callDelegateWithParsingError(
        false, new ApiProxy.RPCFailedException(TEST_PACKAGE_NAME, TEST_METHOD_NAME));
  }

  public void testMakeSyncCall_AllErrors() throws Exception {
    callDelegateWithAllErrors(true);
  }

  public void testMakeAsyncCall_AllErrors() throws Exception {
    callDelegateWithAllErrors(false);
  }

  public void testUnknownRpcError() throws Exception {
    RemoteApiPb.RpcError rpcError = new RemoteApiPb.RpcError();
    rpcError.setCode(123456);
    ApiProxyException expectedException =
        new ApiProxy.UnknownException(TEST_PACKAGE_NAME, TEST_METHOD_NAME);
    callDelegateWithOneError(false, rpcError, null, expectedException);
  }

  public void testCreateRequest() throws Exception {
    VmApiProxyEnvironment environment = createMockEnvironment();
    environment
        .getAttributes()
        .put(VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.attributeKey, "abc123");
    // assertFalse(environment.getForceReuseApiConnection());

    int timeoutMs = 17 * 1000;
    byte[] apiRequestData = new byte[] {1, 2, 3, 4};

    Request request = VmApiProxyDelegate.createRequest(environment, TEST_PACKAGE_NAME,
        TEST_METHOD_NAME, apiRequestData, timeoutMs, new VmApiProxyDelegate().httpclient);
    assertEquals(request.getHeaders()
                     .get(VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.headerKey),
                 "abc123");


    assertEquals(((BytesContentProvider)request.getContent()).getContentType(),
                 "application/octet-stream");
    assertEquals(request.getHeaders().get(VmApiProxyDelegate.RPC_STUB_ID_HEADER),
        VmApiProxyDelegate.REQUEST_STUB_ID);
    assertEquals(request.getHeaders().get(VmApiProxyDelegate.RPC_METHOD_HEADER),
        VmApiProxyDelegate.REQUEST_STUB_METHOD);
    assertEquals(request.getHeaders().get(VmApiProxyDelegate.RPC_DEADLINE_HEADER),
        Double.toString(timeoutMs / 1000));

    // Disable keep-alive, workaround for b/.
    // TODO(b/): revert when the underlying TCP retransmission bug is fixed.
    // assertEquals(request.getFirstHeader("Connection").getValue(), "close");

    RemoteApiPb.Request rmtRequest = new RemoteApiPb.Request();
    assertTrue(rmtRequest.parseFrom((request.getContent()).iterator().next().array()));
    assertEquals(TEST_PACKAGE_NAME, rmtRequest.getServiceName());
    assertEquals(TEST_METHOD_NAME, rmtRequest.getMethod());
    assertEquals(TICKET, rmtRequest.getRequestId());
    assertTrue(Arrays.equals(apiRequestData, rmtRequest.getRequestAsBytes()));
  }

  /*public void testCreateRequest_ForceReuseApiConnection() throws Exception {
    // TODO(b/): delete when the underlying TCP retransmission bug is fixed.
    VmApiProxyEnvironment environment = createMockEnvironment();
    when(environment.getForceReuseApiConnection()).thenReturn(true);
    assertTrue(environment.getForceReuseApiConnection());

    int timeoutMs = 17 * 1000;
    byte[] apiRequestData = new byte[] {1, 2, 3, 4};
    HttpPost request = VmApiProxyDelegate.createRequest(environment, TEST_PACKAGE_NAME,
        TEST_METHOD_NAME, apiRequestData, timeoutMs);

    assertNull(request.getFirstHeader("Connection"));
  }*/

  public void testCreateRequest_DapperHeaderForwarding() throws Exception {
    VmApiProxyEnvironment environment = createMockEnvironment();
    environment
        .getAttributes()
        .put(VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.attributeKey, "abc123");

    Request request = VmApiProxyDelegate.createRequest(environment, TEST_PACKAGE_NAME,
        TEST_METHOD_NAME, new byte[0], 0, new VmApiProxyDelegate().httpclient);
    assertEquals(request.getHeaders()
                     .get(VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.headerKey),
                 "abc123");
  }

  public void testCreateRequest_DeadlineFromEnvironment() throws Exception {
    VmApiProxyEnvironment environment = createMockEnvironment();
    final Double deadline = 10.0;
    environment.getAttributes().put(VmApiProxyDelegate.API_DEADLINE_KEY, deadline);

    Request request = VmApiProxyDelegate.createRequest(environment, TEST_PACKAGE_NAME,
        TEST_METHOD_NAME, new byte[0], 0,new VmApiProxyDelegate().httpclient);
    assertEquals(request.getHeaders().get(VmApiProxyDelegate.RPC_DEADLINE_HEADER),
        Double.toString(deadline));
  }

  public void testApiExceptionWrapping() {
    VmApiProxyDelegate delegate = new VmApiProxyDelegate(createMockHttpClient());
    RuntimeException exception = delegate.constructApiException("logservice", "a");

    assertEquals(LogServiceException.class, exception.getClass());
    assertEquals("RCP Failure for API call: logservice a", exception.getMessage());
    exception = delegate.constructApiException("modules", "b");
    assertEquals(ModulesException.class, exception.getClass());
    assertEquals("RCP Failure for API call: modules b", exception.getMessage());
    exception = delegate.constructApiException("datastore_v3", "c");
    assertEquals(DatastoreFailureException.class, exception.getClass());
    assertEquals("RCP Failure for API call: datastore_v3 c", exception.getMessage());
    exception = delegate.constructApiException("barf", "d");
    assertEquals(ApiProxy.RPCFailedException.class, exception.getClass());
    assertEquals(
        "The remote RPC to the application server failed for the call barf.d().",
        exception.getMessage());
  }

  // TODO move to a common testing framework
  private static class SuppressedLogging implements Closeable {
    Logger logger = Logger.getLogger(VmApiProxyDelegate.class.getName());
    Level level = logger.getLevel();

    SuppressedLogging() {
      // If the logger is not configured to log debug level
      // logs (FINE, FINER, FINEST), then set the logger
      // to SEVERE, so that the expected WARNING messages
      // from these tests are suppressed.
      if (!logger.isLoggable(Level.FINE)) {
        logger.setLevel(Level.SEVERE);
      }
    }

    @Override
    public void close() throws IOException {
      logger.setLevel(level);
    }
  }
}

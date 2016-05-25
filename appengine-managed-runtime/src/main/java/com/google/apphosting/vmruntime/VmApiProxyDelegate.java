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

import com.google.appengine.api.appidentity.AppIdentityServiceFailureException;
import com.google.appengine.api.blobstore.BlobstoreFailureException;
import com.google.appengine.api.channel.ChannelFailureException;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.images.ImagesServiceFailureException;
import com.google.appengine.api.log.LogServiceException;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.search.SearchException;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.appengine.api.users.UserServiceFailureException;
import com.google.appengine.api.xmpp.XMPPFailureException;
import com.google.appengine.repackaged.com.google.common.collect.Lists;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.ApiProxy.RPCFailedException;
import com.google.apphosting.api.UserServicePb.CreateLoginURLResponse;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLResponse;
import com.google.apphosting.utils.remoteapi.RemoteApiPb;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delegates AppEngine API calls to a local http API proxy when running inside a VM.
 *
 * <p>Instances should be registered using ApiProxy.setDelegate(ApiProxy.Delegate).
 *
 */
public class VmApiProxyDelegate implements ApiProxy.Delegate<VmApiProxyEnvironment> {

  private static final Logger logger = Logger.getLogger(VmApiProxyDelegate.class.getName());

  public static final String RPC_DEADLINE_HEADER = "X-Google-RPC-Service-Deadline";
  public static final String RPC_STUB_ID_HEADER = "X-Google-RPC-Service-Endpoint";
  public static final String RPC_METHOD_HEADER = "X-Google-RPC-Service-Method";

  public static final String REQUEST_ENDPOINT = "/rpc_http";
  public static final String REQUEST_STUB_ID = "app-engine-apis";
  public static final String MEMCACHE_REQUEST_STUB_ID = "app-engine-apis/memcache";
  public static final String REQUEST_STUB_METHOD = "/VMRemoteAPI.CallRemoteAPI";

  // This is the same definition as com.google.apphosting.api.API_DEADLINE_KEY. It is also defined
  // here to avoid being exposed to the users in appengine-api.jar.
  protected static final String API_DEADLINE_KEY =
      "com.google.apphosting.api.ApiProxy.api_deadline_key";

  // Default timeout for RPC calls.
  static final int DEFAULT_RPC_TIMEOUT_MS = 60 * 1000;

  // Wait for 1000 ms in addition to the RPC timeout before closing the HTTP connection.
  static final int ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS = 1000;

  protected int defaultTimeoutMs;
  protected final ExecutorService executor;

  protected final HttpClient httpclient;

  final IdleConnectionMonitorThread monitorThread;

  private static ClientConnectionManager createConnectionManager() {
    PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
    connectionManager.setMaxTotal(VmApiProxyEnvironment.MAX_CONCURRENT_API_CALLS);
    connectionManager.setDefaultMaxPerRoute(VmApiProxyEnvironment.MAX_CONCURRENT_API_CALLS);
    return connectionManager;
  }

  public VmApiProxyDelegate() {
    this(new DefaultHttpClient(createConnectionManager()));
  }

  VmApiProxyDelegate(HttpClient httpclient) {
    this.defaultTimeoutMs = DEFAULT_RPC_TIMEOUT_MS;
    this.executor = Executors.newCachedThreadPool();
    this.httpclient = httpclient;
    this.monitorThread = new IdleConnectionMonitorThread(httpclient.getConnectionManager());
    this.monitorThread.start();
  }

  @Override
  public byte[] makeSyncCall(
      VmApiProxyEnvironment environment, String packageName, String methodName, byte[] requestData)
      throws ApiProxyException {
    return makeSyncCallWithTimeout(
        environment, packageName, methodName, requestData, defaultTimeoutMs);
  }

  private byte[] makeSyncCallWithTimeout(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData,
      int timeoutMs)
      throws ApiProxyException {
    return makeApiCall(environment, packageName, methodName, requestData, timeoutMs, false);
  }

  private byte[] makeApiCall(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData,
      int timeoutMs,
      boolean wasAsync) {

    // If this was caused by an async call we need to return the pending call semaphore.
    long start = System.currentTimeMillis();
    environment.apiCallStarted(VmRuntimeUtils.MAX_USER_API_CALL_WAIT_MS, wasAsync);

    try {
      byte responseData[] =
          runSyncCall(environment, packageName, methodName, requestData, timeoutMs);
      long end = System.currentTimeMillis();
      if (logger.isLoggable(Level.FINE)) {
        logger.log(
            Level.FINE,
            String.format(
                "Service bridge API call to package: %s, call: %s, of size: %s "
                    + "complete. Service bridge status code: %s; response "
                    + "content-length: %s. Took %s ms.",
                packageName,
                methodName,
                requestData.length,
                200,
                responseData.length,
                (end - start)));
      }

      // TODO Remove HACK TO FIX USER_SERVICE ISSUE #164
      // Disable with -DUserServiceLocalSchemeHost=false
      if ("user".equals(packageName)) {
        String userservicelocal = System.getProperty("UserServiceLocalSchemeHost");
        String host = (String) environment.getAttributes().get("com.google.appengine.runtime.host");
        String https =
            (String) environment.getAttributes().get("com.google.appengine.runtime.https");
        if ((userservicelocal == null || Boolean.valueOf(userservicelocal))
            && host != null
            && host.length() > 0
            && https != null
            && https.length() > 0) {
          try {
            if ("CreateLogoutURL".equals(methodName)) {
              CreateLogoutURLResponse response = new CreateLogoutURLResponse();
              response.parseFrom(responseData);
              URI uri = new URI(response.getLogoutUrl());
              String query =
                  uri.getQuery()
                      .replaceAll(
                          "https?://[^/]*\\.appspot\\.com",
                          ("on".equalsIgnoreCase(https) ? "https://" : "http://") + host);
              response.setLogoutUrl(
                  new URI(
                          "on".equalsIgnoreCase(https) ? "https" : "http",
                          uri.getUserInfo(),
                          host,
                          uri.getPort(),
                          uri.getPath(),
                          query,
                          uri.getFragment())
                      .toASCIIString());
              return response.toByteArray();
            }
            if ("CreateLoginURL".equals(methodName)) {
              CreateLoginURLResponse response = new CreateLoginURLResponse();
              response.parseFrom(responseData);
              URI uri = new URI(response.getLoginUrl());
              String query =
                  uri.getQuery()
                      .replaceAll(
                          "http?://[^/]*\\.appspot\\.com",
                          ("on".equalsIgnoreCase(https) ? "https://" : "http://") + host);
              response.setLoginUrl(
                  new URI(
                          uri.getScheme(),
                          uri.getUserInfo(),
                          uri.getHost(),
                          uri.getPort(),
                          uri.getPath(),
                          query,
                          uri.getFragment())
                      .toASCIIString());
              return response.toByteArray();
            }
          } catch (URISyntaxException e) {
            logger.log(Level.WARNING, "Problem adjusting UserService URI", e);
          }
        }
      }
      return responseData;
    } catch (Exception e) {
      long end = System.currentTimeMillis();
      int statusCode = 200; // default
      if (e instanceof RPCFailedStatusException) {
        statusCode = ((RPCFailedStatusException) e).getStatusCode();
      }
      logger.log(
          Level.WARNING,
          String.format(
              "Exception during service bridge API call to package: %s, call: %s, "
                  + "of size: %s bytes, status code: %d. Took %s ms. %s",
              packageName,
              methodName,
              requestData.length,
              statusCode,
              (end - start),
              e.getClass().getSimpleName()),
          e);
      throw e;
    } finally {
      environment.apiCallCompleted();
    }
  }

  protected byte[] runSyncCall(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData,
      int timeoutMs) {
    HttpPost request = createRequest(environment, packageName, methodName, requestData, timeoutMs);
    try {
      // Create a new http context for each call as the default context is not thread safe.
      BasicHttpContext context = new BasicHttpContext();
      HttpResponse response = httpclient.execute(request, context);

      // Check for HTTP error status and return early.
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        try (Scanner errorStreamScanner =
            new Scanner(new BufferedInputStream(response.getEntity().getContent())); ) {
          logger.warning("Error body: " + errorStreamScanner.useDelimiter("\\Z").next());
          throw new RPCFailedStatusException(
              packageName, methodName, response.getStatusLine().getStatusCode());
        }
      }
      try (BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent())) {
        RemoteApiPb.Response remoteResponse = new RemoteApiPb.Response();
        if (!remoteResponse.parseFrom(bis)) {
          logger.warning(
              "HTTP ApiProxy unable to parse response for " + packageName + "." + methodName);
          throw new RPCFailedException(packageName, methodName);
        }
        // If the response contains an error, convert it to the expected api exception and throw.
        if (remoteResponse.hasRpcError() || remoteResponse.hasApplicationError()) {
          throw convertRemoteError(remoteResponse, packageName, methodName, logger);
        }
        // Success, return the response.
        return remoteResponse.getResponseAsBytes();
      }
    } catch (IOException e) {
      logger.warning(
          "HTTP ApiProxy I/O error for " + packageName + "." + methodName + ": " + e.getMessage());
      throw constructApiException(packageName, methodName);
    } finally {
      request.releaseConnection();
    }
  }

  // TODO(ludo) remove when the correct exceptions have public constructor.
  private RuntimeException constructException(
      String exceptionClassName, String message, String packageName, String methodName) {
    try {
      Class<?> c = Class.forName(exceptionClassName);
      Constructor<?> constructor = c.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      return (RuntimeException) constructor.newInstance(message);
    } catch (Exception e) {
      return new RPCFailedException(packageName, methodName);
    }
  }

  RuntimeException constructApiException(String packageName, String methodName) {
    String message = "RCP Failure for API call: " + packageName + " " + methodName;

    switch (packageName) {
      case "taskqueue":
        return new TransientFailureException(message);
      case "app_identity_service":
        return new AppIdentityServiceFailureException(message);
      case "blobstore":
        return new BlobstoreFailureException(message);
      case "channel":
        return new ChannelFailureException(message);
      case "images":
        return new ImagesServiceFailureException(message);
      case "logservice":
        return constructException(
            LogServiceException.class.getName(), message, packageName, methodName);
      case "memcache":
        return new MemcacheServiceException(message);
      case "modules":
        return constructException(
            ModulesException.class.getName(), message, packageName, methodName);
      case "search":
        return new SearchException(message);
      case "user":
        return new UserServiceFailureException(message);
      case "xmpp":
        return new XMPPFailureException(message);
      default:

        // Cover all datastore versions:
        if (packageName.startsWith("datastore")) {
          return new DatastoreFailureException(message);
        } else {
          return new RPCFailedException(packageName, methodName);
        }
    }
  }
  /**
   * Create an HTTP post request suitable for sending to the API server.
   *
   * @param environment The current VMApiProxyEnvironment
   * @param packageName The API call package
   * @param methodName The API call method
   * @param requestData The POST payload.
   * @param timeoutMs The timeout for this request
   * @return an HttpPost object to send to the API.
   */
  //
  static HttpPost createRequest(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] requestData,
      int timeoutMs) {
    // Wrap the payload in a RemoteApi Request.
    RemoteApiPb.Request remoteRequest = new RemoteApiPb.Request();
    remoteRequest.setServiceName(packageName);
    remoteRequest.setMethod(methodName);
    remoteRequest.setRequestId(environment.getTicket());
    remoteRequest.setRequestAsBytes(requestData);

    HttpPost request = new HttpPost("http://" + environment.getServer() + REQUEST_ENDPOINT);
    if (packageName.equals("memcache")) {
      request.setHeader(RPC_STUB_ID_HEADER, MEMCACHE_REQUEST_STUB_ID);
    } else {
      request.setHeader(RPC_STUB_ID_HEADER, REQUEST_STUB_ID);
    }
    request.setHeader(RPC_METHOD_HEADER, REQUEST_STUB_METHOD);

    // Set TCP connection timeouts.
    HttpParams params = new BasicHttpParams();
    params.setLongParameter(
        ConnManagerPNames.TIMEOUT, timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);
    params.setIntParameter(
        CoreConnectionPNames.CONNECTION_TIMEOUT, timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);
    params.setIntParameter(
        CoreConnectionPNames.SO_TIMEOUT, timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);

    // Performance tweaks.
    params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, Boolean.TRUE);
    params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, Boolean.FALSE);
    request.setParams(params);

    // The request deadline can be overwritten by the environment, read deadline if available.
    Double deadline = (Double) (environment.getAttributes().get(API_DEADLINE_KEY));
    if (deadline == null) {
      request.setHeader(
          RPC_DEADLINE_HEADER,
          Double.toString(TimeUnit.SECONDS.convert(timeoutMs, TimeUnit.MILLISECONDS)));
    } else {
      request.setHeader(RPC_DEADLINE_HEADER, Double.toString(deadline));
    }

    // If the incoming request has a dapper trace header: set it on outgoing API calls
    // so they are tied to the original request.
    Object dapperHeader =
        environment
            .getAttributes()
            .get(VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.attributeKey);
    if (dapperHeader instanceof String) {
      request.setHeader(
          VmApiProxyEnvironment.AttributeMapping.DAPPER_ID.headerKey, (String) dapperHeader);
    }

    // If the incoming request has a Cloud trace header: set it on outgoing API calls
    // so they are tied to the original request.
    // TODO(user): For now, this uses the incoming span id - use the one from the active span.
    Object traceHeader =
        environment
            .getAttributes()
            .get(VmApiProxyEnvironment.AttributeMapping.CLOUD_TRACE_CONTEXT.attributeKey);
    if (traceHeader instanceof String) {
      request.setHeader(
          VmApiProxyEnvironment.AttributeMapping.CLOUD_TRACE_CONTEXT.headerKey,
          (String) traceHeader);
    }

    ByteArrayEntity postPayload =
        new ByteArrayEntity(remoteRequest.toByteArray(), ContentType.APPLICATION_OCTET_STREAM);
    postPayload.setChunked(false);
    request.setEntity(postPayload);

    return request;
  }

  /**
   * Convert RemoteApiPb.Response errors to the appropriate exception.
   *
   * <p>The response must have exactly one of the RpcError and ApplicationError fields set.
   *
   * @param remoteResponse the Response
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param logger the Logger used to create log messages.
   * @return ApiProxyException
   */
  private static ApiProxyException convertRemoteError(
      RemoteApiPb.Response remoteResponse, String packageName, String methodName, Logger logger) {
    if (remoteResponse.hasRpcError()) {
      return convertApiResponseRpcErrorToException(
          remoteResponse.getRpcError(), packageName, methodName, logger);
    }

    // Otherwise it's an application error
    RemoteApiPb.ApplicationError error = remoteResponse.getApplicationError();
    return new ApiProxy.ApplicationException(error.getCode(), error.getDetail());
  }

  /**
   * Convert the RemoteApiPb.RpcError to the appropriate exception.
   *
   * @param rpcError the RemoteApiPb.RpcError.
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param logger the Logger used to create log messages.
   * @return ApiProxyException
   */
  private static ApiProxyException convertApiResponseRpcErrorToException(
      RemoteApiPb.RpcError rpcError, String packageName, String methodName, Logger logger) {

    int rpcCode = rpcError.getCode();
    String errorDetail = rpcError.getDetail();
    if (rpcCode > RemoteApiPb.RpcError.ErrorCode.values().length) {
      logger.severe(
          "Received unrecognized error code from server: "
              + rpcError.getCode()
              + " details: "
              + errorDetail);
      return new ApiProxy.UnknownException(packageName, methodName);
    }
    RemoteApiPb.RpcError.ErrorCode errorCode =
        RemoteApiPb.RpcError.ErrorCode.values()[rpcError.getCode()];
    logger.warning(
        "RPC failed, API="
            + packageName
            + "."
            + methodName
            + " : "
            + errorCode
            + " : "
            + errorDetail);

    // This is very similar to apphosting/utils/runtime/ApiProxyUtils.java#convertApiError,
    // which is for APIResponse. TODO(user): retire both in favor of gRPC.
    switch (errorCode) {
      case CALL_NOT_FOUND:
        return new ApiProxy.CallNotFoundException(packageName, methodName);
      case PARSE_ERROR:
        return new ApiProxy.ArgumentException(packageName, methodName);
      case SECURITY_VIOLATION:
        logger.severe("Security violation: invalid request id used!");
        return new ApiProxy.UnknownException(packageName, methodName);
      case CAPABILITY_DISABLED:
        return new ApiProxy.CapabilityDisabledException(errorDetail, packageName, methodName);
      case OVER_QUOTA:
        return new ApiProxy.OverQuotaException(packageName, methodName);
      case REQUEST_TOO_LARGE:
        return new ApiProxy.RequestTooLargeException(packageName, methodName);
      case RESPONSE_TOO_LARGE:
        return new ApiProxy.ResponseTooLargeException(packageName, methodName);
      case BAD_REQUEST:
        return new ApiProxy.ArgumentException(packageName, methodName);
      case CANCELLED:
        return new ApiProxy.CancelledException(packageName, methodName);
      case FEATURE_DISABLED:
        return new ApiProxy.FeatureNotEnabledException(errorDetail, packageName, methodName);
      case DEADLINE_EXCEEDED:
        return new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
      default:
        return new ApiProxy.UnknownException(packageName, methodName);
    }
  }

  private class MakeSyncCall implements Callable<byte[]> {
    private final VmApiProxyDelegate delegate;
    private final VmApiProxyEnvironment environment;
    private final String packageName;
    private final String methodName;
    private final byte[] requestData;
    private final int timeoutMs;

    public MakeSyncCall(
        VmApiProxyDelegate delegate,
        VmApiProxyEnvironment environment,
        String packageName,
        String methodName,
        byte[] requestData,
        int timeoutMs) {
      this.delegate = delegate;
      this.environment = environment;
      this.packageName = packageName;
      this.methodName = methodName;
      this.requestData = requestData;
      this.timeoutMs = timeoutMs;
    }

    @Override
    public byte[] call() throws Exception {
      return delegate.makeApiCall(
          environment, packageName, methodName, requestData, timeoutMs, true);
    }
  }

  @Override
  public Future<byte[]> makeAsyncCall(
      VmApiProxyEnvironment environment,
      String packageName,
      String methodName,
      byte[] request,
      ApiConfig apiConfig) {
    int timeoutMs = defaultTimeoutMs;
    if (apiConfig != null && apiConfig.getDeadlineInSeconds() != null) {
      timeoutMs = (int) (apiConfig.getDeadlineInSeconds() * 1000);
    }
    environment.aSyncApiCallAdded(VmRuntimeUtils.MAX_USER_API_CALL_WAIT_MS);
    return executor.submit(
        new MakeSyncCall(this, environment, packageName, methodName, request, timeoutMs));
  }

  @Override
  public void log(VmApiProxyEnvironment environment, LogRecord record) {
    if (environment != null) {
      environment.addLogRecord(record);
    }
  }

  @Override
  public void flushLogs(VmApiProxyEnvironment environment) {
    if (environment != null) {
      environment.flushLogs();
    }
  }

  @Override
  public List<Thread> getRequestThreads(VmApiProxyEnvironment environment) {
    Object threadFactory =
        environment.getAttributes().get(VmApiProxyEnvironment.REQUEST_THREAD_FACTORY_ATTR);
    if (threadFactory != null && threadFactory instanceof VmRequestThreadFactory) {
      return ((VmRequestThreadFactory) threadFactory).getRequestThreads();
    }
    logger.warning("Got a call to getRequestThreads() but no VmRequestThreadFactory is available");
    return Lists.newLinkedList();
  }

  /**
   * Simple connection watchdog verifying that our connections are alive. Any stale connections are
   * cleared as well.
   */
  class IdleConnectionMonitorThread extends Thread {

    private final ClientConnectionManager connectionManager;

    public IdleConnectionMonitorThread(ClientConnectionManager connectionManager) {
      super("IdleApiConnectionMontorThread");
      this.connectionManager = connectionManager;
      this.setDaemon(true);
    }

    @Override
    public void run() {
      try {
        while (true) {
          // Close expired connections.
          connectionManager.closeExpiredConnections();
          // Close connections that have been idle longer than 60 sec.
          connectionManager.closeIdleConnections(60, TimeUnit.SECONDS);
          Thread.sleep(5000);
        }
      } catch (InterruptedException ex) {
        // terminate
      }
    }
  }
}

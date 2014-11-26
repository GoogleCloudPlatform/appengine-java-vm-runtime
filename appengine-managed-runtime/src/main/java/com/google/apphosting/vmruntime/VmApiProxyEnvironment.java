/**
 * Copyright 2012 Google Inc. All Rights Reserved.
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


package com.google.apphosting.vmruntime;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.logservice.LogServicePb.UserAppLogLine;
import com.google.apphosting.runtime.timer.Timer;
import com.google.apphosting.utils.http.HttpRequest;


import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Implements the ApiProxy environment when running in a Google Compute Engine VM.
 *
 * Supports instantiation within a request as well as outside the context of a request.
 *
 * Instances should be registered using ApiProxy.setEnvironmentForCurrentThread(Environment).
 *
 */
public class VmApiProxyEnvironment implements ApiProxy.Environment {

  public static final String PROJECT_ATTRIBUTE = "attributes/gae_project";
  static final String LONG_APP_ID_KEY = "GAE_LONG_APP_ID";

  public static final String PARTITION_ATTRIBUTE = "attributes/gae_partition";
  static final String PARTITION_KEY = "GAE_PARTITION";

  static final String GAE_SERVER_PORT = "GAE_SERVER_PORT";

  public static final String BACKEND_ATTRIBUTE = "attributes/gae_backend_name";
  static final String MODULE_NAME_KEY = "GAE_MODULE_NAME";

  public static final String VERSION_ATTRIBUTE = "attributes/gae_backend_version";
  static final String VERSION_KEY = "GAE_MODULE_VERSION";

  public static final String INSTANCE_ATTRIBUTE = "attributes/gae_backend_instance";
  static final String INSTANCE_KEY = "GAE_MODULE_INSTANCE";

  static final String MINOR_VERSION_KEY = "GAE_MINOR_VERSION";

  public static final String TICKET_HEADER = "X-AppEngine-Api-Ticket";
  public static final String EMAIL_HEADER = "X-AppEngine-User-Email";
  public static final String IS_ADMIN_HEADER = "X-AppEngine-User-Is-Admin";
  public static final String AUTH_DOMAIN_HEADER = "X-AppEngine-Auth-Domain";
  public static final String HTTPS_HEADER = "X-AppEngine-Https";

  
  static final String BACKEND_ID_KEY = "com.google.appengine.backend.id";
  

  static final String INSTANCE_ID_KEY = "com.google.appengine.instance.id";

  static final String REQUEST_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.REQUEST_THREAD_FACTORY";
  static final String BACKGROUND_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.BACKGROUND_THREAD_FACTORY";

  

  static final String IS_FEDERATED_USER_KEY =
      "com.google.appengine.api.users.UserService.is_federated_user";

  
  static final String IS_TRUSTED_IP_KEY = "com.google.appengine.runtime.is_trusted_ip";
  
  static final String IS_TRUSTED_IP_HEADER = "X-AppEngine-Trusted-IP-Request";

  private static final long DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT = 1024 * 1024L;
  private static final int MAX_LOG_FLUSH_SECONDS = 60;
  private static final int DEFAULT_MAX_LOG_LINE_SIZE = 8 * 1024;

  
  static final int MAX_CONCURRENT_API_CALLS = 100;
  
  static final int MAX_PENDING_API_CALLS = 1000;

  /**
   * Mapping from HTTP header keys to attribute keys.
   */
  static enum AttributeMapping {
    USER_ID(
        "X-AppEngine-User-Id",
        "com.google.appengine.api.users.UserService.user_id_key",
        "", false),
    USER_ORGANIZATION(
        "X-AppEngine-User-Organization",
        "com.google.appengine.api.users.UserService.user_organization",
        "", false),
    FEDERATED_IDENTITY(
        "X-AppEngine-Federated-Identity",
        "com.google.appengine.api.users.UserService.federated_identity",
        "", false),
    FEDERATED_PROVIDER(
        "X-AppEngine-Federated-Provider",
        "com.google.appengine.api.users.UserService.federated_authority",
        "", false),
    DATACENTER(
        "X-AppEngine-Datacenter",
        "com.google.apphosting.api.ApiProxy.datacenter",
        "", false),
    REQUEST_ID_HASH(
        "X-AppEngine-Request-Id-Hash",
        "com.google.apphosting.api.ApiProxy.request_id_hash",
        null, false),
    REQUEST_LOG_ID(
        "X-AppEngine-Request-Log-Id",
        "com.google.appengine.runtime.request_log_id",
        null, false),
    DAPPER_ID("X-Google-DapperTraceInfo",
              "com.google.appengine.runtime.dapper_id",
              null, false),
    DEFAULT_VERSION_HOSTNAME(
        "X-AppEngine-Default-Version-Hostname",
        "com.google.appengine.runtime.default_version_hostname",
        null, false),
    DEFAULT_NAMESPACE_HEADER(
        "X-AppEngine-Default-Namespace",
        "com.google.appengine.api.NamespaceManager.appsNamespace",
        null, false),
    CURRENT_NAMESPACE_HEADER(
        "X-AppEngine-Current-Namespace",
        "com.google.appengine.api.NamespaceManager.currentNamespace",
        null, false),
    LOAS_PEER_USERNAME(
        "X-AppEngine-LOAS-Peer-Username",
        "com.google.net.base.peer.loas_peer_username",
        "", true),
    GAIA_ID(
        "X-AppEngine-Gaia-Id",
        "com.google.appengine.runtime.gaia_id",
        "", true),
    GAIA_AUTHUSER(
        "X-AppEngine-Gaia-Authuser",
        "com.google.appengine.runtime.gaia_authuser",
        "", true),
    GAIA_SESSION(
        "X-AppEngine-Gaia-Session",
        "com.google.appengine.runtime.gaia_session",
        "", true),
    APPSERVER_DATACENTER(
        "X-AppEngine-Appserver-Datacenter",
        "com.google.appengine.runtime.appserver_datacenter",
        "", true),
    APPSERVER_TASK_BNS(
        "X-AppEngine-Appserver-Task-Bns",
        "com.google.appengine.runtime.appserver_task_bns",
        "", true);

    String headerKey;
    String attributeKey;
    Object defaultValue;
    private boolean trustedAppOnly;

    /**
     * Creates a mapping between an incoming request header and the thread local request attribute
     * corresponding to that header.
     *
     * @param headerKey The HTTP header key.
     * @param attributeKey The attribute key.
     * @param defaultValue The default value to set if the header is missing, or null if no
     *        attribute should be set when the header is missing.
     * @param trustedAppOnly If true the attribute should only be set for trusted apps.
     */
    private AttributeMapping(
        String headerKey, String attributeKey, Object defaultValue, boolean trustedAppOnly) {
      this.headerKey = headerKey;
      this.attributeKey = attributeKey;
      this.defaultValue = defaultValue;
      this.trustedAppOnly = trustedAppOnly;
    }
  }

  /**
   * Helper method to use during the transition from metadata to environment variables.
   *
   * @param environmentMap the
   * @param envKey The name of the environment variable to check first.
   * @param metadataPath The path of the metadata server entry to use as fallback.
   * @param cache The metadata server cache.
   * @return If set the environment variable corresponding to envKey, the metadata entry otherwise.
   */
  private static String getEnvOrMetadata(Map<String, String> environmentMap, VmMetadataCache cache,
      String envKey, String metadataPath) {
    String envValue = environmentMap.get(envKey);
    return envValue != null ? envValue : cache.getMetadata(metadataPath);
  }

  /**
   * Creates an environment for AppEngine API calls outside the context of a request.
   *
   * @param envMap a map containing environment variables (from System.getenv()).
   * @param cache the VM meta-data cache used to retrieve VM attributes.
   * @param server the host:port where the VMs API proxy is bound to.
   * @param wallTimer optional wall clock timer for the current request (required for deadline).
   * @param millisUntilSoftDeadline optional soft deadline in milliseconds relative to 'wallTimer'.
   * @param appDir the canonical folder of the application.
   * @return the created Environment object which can be registered with the ApiProxy.
   */
  public static VmApiProxyEnvironment createDefaultContext(Map<String, String> envMap,
      VmMetadataCache cache,
      String server,
      Timer wallTimer,
      Long millisUntilSoftDeadline,
      String appDir) {
    final String longAppId = getEnvOrMetadata(envMap, cache, LONG_APP_ID_KEY, PROJECT_ATTRIBUTE);
    final String partition = getEnvOrMetadata(envMap, cache, PARTITION_KEY, PARTITION_ATTRIBUTE);
    final String module = getEnvOrMetadata(envMap, cache, MODULE_NAME_KEY, BACKEND_ATTRIBUTE);
    final String majorVersion = getEnvOrMetadata(envMap, cache, VERSION_KEY, VERSION_ATTRIBUTE);
    String minorVersion = envMap.get(MINOR_VERSION_KEY);
    if (minorVersion == null) {
      minorVersion = VmRuntimeUtils.getMinorVersionFromPath(majorVersion, appDir);
    }
    final String instance = getEnvOrMetadata(envMap, cache, INSTANCE_KEY, INSTANCE_ATTRIBUTE);
    final String ticket = null;
    final String email = null;
    final boolean admin = false;
    final String authDomain = null;
    Map<String, Object> attributes = new HashMap<String, Object>();
    for (AttributeMapping mapping : AttributeMapping.values()) {
      if (mapping.trustedAppOnly) {
        continue;
      }
      if (mapping.defaultValue == null) {
        continue;
      }
      attributes.put(mapping.attributeKey, mapping.defaultValue);
    }
    attributes.put(IS_FEDERATED_USER_KEY, Boolean.FALSE);
    attributes.put(BACKEND_ID_KEY, module);
    attributes.put(INSTANCE_ID_KEY, instance);
    VmApiProxyEnvironment defaultEnvironment = new VmApiProxyEnvironment(server, ticket, longAppId,
        partition, module, majorVersion, minorVersion, instance, email, admin, authDomain,
        wallTimer, millisUntilSoftDeadline, attributes);
    attributes.put(REQUEST_THREAD_FACTORY_ATTR, new VmRequestThreadFactory(null));
    attributes.put(BACKGROUND_THREAD_FACTORY_ATTR, Executors.defaultThreadFactory());
    return defaultEnvironment;
  }

  /**
   * Create an environment for AppEngine API calls in the context of a request.
   *
   * @param envMap a map containing environment variables (from System.getenv()).
   * @param cache the VM meta-data cache used to retrieve VM attributes.
   * @param request the HTTP request to get header values from.
   * @param server the host:port where the VMs API proxy is bound to.
   * @param wallTimer optional wall clock timer for the current request (required for deadline).
   * @param millisUntilSoftDeadline optional soft deadline in milliseconds relative to 'wallTimer'.
   * @return the created Environment object which can be registered with the ApiProxy.
   */
  public static VmApiProxyEnvironment createFromHeaders(Map<String, String> envMap,
      VmMetadataCache cache,
      HttpRequest request,
      String server,
      Timer wallTimer,
      Long millisUntilSoftDeadline,
      VmApiProxyEnvironment defaultEnvironment) {
    final String longAppId = getEnvOrMetadata(envMap, cache, LONG_APP_ID_KEY, PROJECT_ATTRIBUTE);
    final String partition = getEnvOrMetadata(envMap, cache, PARTITION_KEY, PARTITION_ATTRIBUTE);
    final String module = getEnvOrMetadata(envMap, cache, MODULE_NAME_KEY, BACKEND_ATTRIBUTE);
    final String majorVersion = defaultEnvironment.getMajorVersion();
    final String minorVersion = defaultEnvironment.getMinorVersion();
    final String instance = getEnvOrMetadata(envMap, cache, INSTANCE_KEY, INSTANCE_ATTRIBUTE);
    final String ticket = request.getHeader(TICKET_HEADER);
    final String email = request.getHeader(EMAIL_HEADER);
    boolean admin = false;
    String value = request.getHeader(IS_ADMIN_HEADER);
    if (value != null && !value.trim().isEmpty()) {
      try {
        admin = Integer.parseInt(value.trim()) != 0;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
    final String authDomain = request.getHeader(AUTH_DOMAIN_HEADER);
    boolean trustedApp = request.getHeader(IS_TRUSTED_IP_HEADER) != null;

    Map<String, Object> attributes = new HashMap<String, Object>();
    for (AttributeMapping mapping : AttributeMapping.values()) {
      if (mapping.trustedAppOnly && !trustedApp) {
        continue;
      }
      String headerValue = request.getHeader(mapping.headerKey);
      if (headerValue != null) {
        attributes.put(mapping.attributeKey, headerValue);
      } else if (mapping.defaultValue != null) {
        attributes.put(mapping.attributeKey, mapping.defaultValue);
      }
    }

    boolean federatedId = request.getHeader(AttributeMapping.FEDERATED_IDENTITY.headerKey) != null;
    attributes.put(IS_FEDERATED_USER_KEY, Boolean.valueOf(federatedId));

    attributes.put(BACKEND_ID_KEY, module);
    attributes.put(INSTANCE_ID_KEY, instance);

    if (trustedApp) {
      boolean trustedIp = "1".equals(request.getHeader(IS_TRUSTED_IP_HEADER));
      attributes.put(IS_TRUSTED_IP_KEY, Boolean.valueOf(trustedIp));
    }

    VmApiProxyEnvironment requestEnvironment = new VmApiProxyEnvironment(server, ticket, longAppId,
        partition, module, majorVersion, minorVersion, instance, email, admin, authDomain,
        wallTimer, millisUntilSoftDeadline, attributes);
    attributes.put(REQUEST_THREAD_FACTORY_ATTR, new VmRequestThreadFactory(requestEnvironment));
    attributes.put(BACKGROUND_THREAD_FACTORY_ATTR, Executors.defaultThreadFactory());

    return requestEnvironment;
  }

  private final String server;
  private final String ticket;
  private final int serverPort;
  private final String partition;
  private final String appId;
  private final String module;
  private final String majorVersion;
  private final String minorVersion;
  private final String versionId;
  private final String instance;
  private final String email;
  private final boolean admin;
  private final String authDomain;
  private final Map<String, Object> attributes;
  private ThreadLocal<Map<String, Object>> threadLocalAttributes;
  private final Timer wallTimer;
  private final Long millisUntilSoftDeadline;
  private final VmAppLogsWriter appLogsWriter;
  
  final Semaphore pendingApiCallSemaphore;
  
  final Semaphore runningApiCallSemaphore;

  /**
   * Constructs a VM AppEngine API environment.
   *
   * @param server the host:port address of the VM's HTTP proxy server.
   * @param ticket the request ticket (if null the default one will be computed).
   * @param appId the application ID (required if ticket is null).
   * @param partition the partition name.
   * @param module the module name (required if ticket is null).
   * @param majorVersion the major application version (required if ticket is null).
   * @param minorVersion the minor application version.
   * @param instance the VM instance ID (required if ticket is null).
   * @param email the user's e-mail address (may be null).
   * @param admin true if the user is an administrator.
   * @param authDomain the user's authentication domain (may be null).
   * @param wallTimer optional wall clock timer for the current request (required for deadline).
   * @param millisUntilSoftDeadline optional soft deadline in milliseconds relative to 'wallTimer'.
   * @param attributes map containing any attributes set on this environment.
   */
  private VmApiProxyEnvironment(
      String server, String ticket, String appId, String partition, String module,
      String majorVersion, String minorVersion, String instance, String email, boolean admin,
      String authDomain, Timer wallTimer, Long millisUntilSoftDeadline,
      Map<String, Object> attributes) {
    if (server == null || server.isEmpty()) {
      throw new IllegalArgumentException("proxy server host:port must be specified");
    }
    if (millisUntilSoftDeadline != null && wallTimer == null) {
      throw new IllegalArgumentException("wallTimer required when setting millisUntilSoftDeadline");
    }
    if (ticket == null || ticket.isEmpty()) {
      if ((appId == null || appId.isEmpty()) ||
          (module == null || module.isEmpty()) ||
          (majorVersion == null || majorVersion.isEmpty()) ||
          (instance == null || instance.isEmpty())) {
        throw new IllegalArgumentException(
            "When ticket == null the following must be specified: appId=" + appId +
            ", module=" + module + ", version=" + majorVersion + "instance=" + instance);
      }
      String escapedAppId = appId.replace(':', '_').replace('.', '_');
      this.ticket = escapedAppId + '/' + module + '.' + majorVersion + "." + instance;
    } else {
      this.ticket = ticket;
    }
    this.server = server;
    this.partition = partition;
    String port = System.getenv(GAE_SERVER_PORT) == null ? "80" : System.getenv(GAE_SERVER_PORT);
    this.serverPort = Integer.decode(port);
    if (appId == null) {
      this.appId = "";
    } else {
      this.appId = partition + "~" + appId;
    }
    this.module = module == null ? "default" : module;
    this.majorVersion = majorVersion == null ? "" : majorVersion;
    this.minorVersion = minorVersion == null ? "" : minorVersion;
    this.versionId = String.format("%s.%s", this.majorVersion, this.minorVersion);
    this.instance = instance == null ? "" : instance;
    this.email = email == null ? "" : email;
    this.admin = admin;
    this.authDomain = authDomain == null ? "" : authDomain;
    this.wallTimer = wallTimer;
    this.millisUntilSoftDeadline = millisUntilSoftDeadline;
    this.attributes = Collections.synchronizedMap(attributes);

    this.appLogsWriter = new VmAppLogsWriter(
        new LinkedList<UserAppLogLine>(), DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT,
        DEFAULT_MAX_LOG_LINE_SIZE, MAX_LOG_FLUSH_SECONDS);
    this.pendingApiCallSemaphore = new Semaphore(MAX_PENDING_API_CALLS);
    this.runningApiCallSemaphore = new Semaphore(MAX_CONCURRENT_API_CALLS);
  }

  public void addLogRecord(LogRecord record) {
    appLogsWriter.addLogRecordAndMaybeFlush(record);
  }

  public void flushLogs() {
    appLogsWriter.flushAndWait();
  }

  public int flushLogsAsync() {
    return appLogsWriter.waitForCurrentFlushAndStartNewFlush();
  }

  public String getMajorVersion() {
    return majorVersion;
  }

  public String getMinorVersion() {
    return minorVersion;
  }

  public String getServer() {
    return server;
  }

  public String getTicket() {
    return ticket;
  }

  public String getPartition() {
    return partition;
  }

  public int getServerPort() {
    return serverPort;
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public String getModuleId() {
    return module;
  }

  @Override
  public String getVersionId() {
    return versionId;
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public boolean isLoggedIn() {
    return getEmail() != null && !getEmail().trim().isEmpty();
  }

  @Override
  public boolean isAdmin() {
    return admin;
  }

  @Override
  public String getAuthDomain() {
    return authDomain;
  }

  @Deprecated
  @Override
  public String getRequestNamespace() {
    Object currentNamespace =
        attributes.get(AttributeMapping.CURRENT_NAMESPACE_HEADER.attributeKey);
    if (currentNamespace instanceof String) {
      return (String) currentNamespace;
    }
    return "";
  }

  /***
   * Create a thread-local copy of the attributes. Used for the instance of this class that is
   * shared among all the background threads. They each may mutate the attributes independently.
   */
  public synchronized void setThreadLocalAttributes() {
    if (threadLocalAttributes == null) {
      threadLocalAttributes = new ThreadLocal<Map<String, Object>>();
    }
    threadLocalAttributes.set(new HashMap<String, Object>(attributes));
  }

  @Override
  public Map<String, Object> getAttributes() {
    if (threadLocalAttributes != null && threadLocalAttributes.get() != null) {
      return threadLocalAttributes.get();
    } else {
      return attributes;
    }
  }

  @Override
  public long getRemainingMillis() {
    if (millisUntilSoftDeadline == null) {
      return Long.MAX_VALUE;
    }
    return millisUntilSoftDeadline - (wallTimer.getNanoseconds() / 1000000L);
  }

  /**
   * Notifies the environment that an API call was queued up.
   *
   * @throws ApiProxyException
   */
  void aSyncApiCallAdded(long maxWaitMs) throws ApiProxyException {
    try {
      if (pendingApiCallSemaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS)) {
        return;
      }
      throw new ApiProxyException("Timed out while acquiring a pending API call semaphore.");
    } catch (InterruptedException e) {
      throw new ApiProxyException(
          "Thread interrupted while acquiring a pending API call semaphore.");
    }
  }

  /**
   * Notifies the environment that an API call was started.
   *
   * @param releasePendingCall If true a pending call semaphore will be released (required if this
   *        API call was requested asynchronously).
   *
   * @throws ApiProxyException If the thread was interrupted while waiting for a semaphore.
   */
  void apiCallStarted(long maxWaitMs, boolean releasePendingCall) throws ApiProxyException {
    try {
      if (runningApiCallSemaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS)) {
        return;
      }
      throw new ApiProxyException("Timed out while acquiring an API call semaphore.");
    } catch (InterruptedException e) {
      throw new ApiProxyException("Thread interrupted while acquiring an API call semaphore.");
    } finally {
      if (releasePendingCall) {
        pendingApiCallSemaphore.release();
      }
    }
  }

  /**
   * Notifies the environment that an API call completed.
   */
  void apiCallCompleted() {
    runningApiCallSemaphore.release();
  }

  /**
   * Waits for up to {@code maxWaitMs} ms for all outstanding API calls to complete.
   *
   * @param maxWaitMs The maximum time to wait.
   * @return True if the all calls completed before the timeout fired or the thread was interrupted.
   *         False otherwise.
   */
  public boolean waitForAllApiCallsToComplete(long maxWaitMs) {
    try {
      long startTime = System.currentTimeMillis();
      if (pendingApiCallSemaphore.tryAcquire(
          MAX_PENDING_API_CALLS, maxWaitMs, TimeUnit.MILLISECONDS)) {
        try {
          long remaining = maxWaitMs - (System.currentTimeMillis() - startTime);
          if (runningApiCallSemaphore.tryAcquire(
              MAX_CONCURRENT_API_CALLS, remaining, TimeUnit.MILLISECONDS)) {
            runningApiCallSemaphore.release(MAX_CONCURRENT_API_CALLS);
            return true;
          }
        } finally {
          pendingApiCallSemaphore.release(MAX_PENDING_API_CALLS);
        }
      }
    } catch (InterruptedException ignored) {
    }
    return false;
  }
}

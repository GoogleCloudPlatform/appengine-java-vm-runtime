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

package com.google.apphosting.utils.config;

import com.google.appengine.repackaged.com.google.common.base.CharMatcher;
import com.google.appengine.repackaged.com.google.common.collect.Lists;
import com.google.appengine.repackaged.com.google.common.collect.Maps;

import java.security.Permissions;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Struct describing the config data that lives in WEB-INF/appengine-web.xml.
 *
 * Any additions to this class should also be made to the YAML
 * version in AppYaml.java.
 *
 */
public class AppEngineWebXml implements Cloneable {
  /**
   * Enumeration of supported scaling types.
   */
  public static enum ScalingType {
    AUTOMATIC,
    MANUAL,
    BASIC
  }

  // System properties defined by the application in appengine-web.xml
  private final Map<String, String> systemProperties = Maps.newHashMap();

  // Beta settings defined by the application in appengine-web.xml.
  // Using a linked hash map is not strictly needed but makes testing easier.
  private final Map<String, String> betaSettings = Maps.newLinkedHashMap();

  private final HealthCheck healthCheck;

  private final Resources resources;

  private final Network network;

  // Environment variables defined by the application in appengine-web.xml
  private final Map<String, String> envVariables = Maps.newHashMap();

  private final List<UserPermission> userPermissions = new ArrayList<UserPermission>();

  public static final String WARMUP_SERVICE = "warmup";

  public static final String URL_HANDLER_URLFETCH = "urlfetch";
  public static final String URL_HANDLER_NATIVE = "native";

  // Runtime ids.
  private static final String JAVA_7_RUNTIME_ID = "java7";
  // Should accept java8* for multiple variations of Java8.
  private static final String JAVA_8_RUNTIME_ID = "java8";
  // This was used for Java6, but now is used only for Managed VMs,
  // not for standard editiom.
  private static final String JAVA_RUNTIME_ID = "java";

  private String appId;

  private String majorVersionId;

  private String module;
  // Used to be module, but now replaced by service.
  private String service;
  private String instanceClass;

  private final AutomaticScaling automaticScaling;
  private final ManualScaling manualScaling;
  private final BasicScaling basicScaling;

  private String runtime;
  private boolean sslEnabled = true;
  private boolean useSessions = false;
  private boolean asyncSessionPersistence = false;
  private String asyncSessionPersistenceQueueName;

  private final List<StaticFileInclude> staticFileIncludes;
  private final List<String> staticFileExcludes;
  private final List<String> resourceFileIncludes;
  private final List<String> resourceFileExcludes;

  private Pattern staticIncludePattern;
  private Pattern staticExcludePattern;
  private Pattern resourceIncludePattern;
  private Pattern resourceExcludePattern;

  private String publicRoot = "";

  private String appRoot;

  private final Set<String> inboundServices;
  private boolean precompilationEnabled = true;

  private final List<AdminConsolePage> adminConsolePages = new ArrayList<AdminConsolePage>();
  private final List<ErrorHandler> errorHandlers = new ArrayList<ErrorHandler>();

  private ClassLoaderConfig classLoaderConfig;

  private String urlStreamHandlerType = null;

  // TODO(user): Set this to true at some future point.
  private boolean threadsafe = false;
  // Keeps track of whether or not there was a <threadsafe> element in the xml.
  // It would be easier to just make the threadsafe member variable a Boolean
  // but we can't change the public signature of this method.
  private boolean threadsafeValueProvided = false;

  // TODO(user): Revert once sequential auto id deprecation period expires.
  private String autoIdPolicy;

  private boolean codeLock = false;
  private boolean useVm = false;
  // Default env is standard (GAE V1)
  // TODO(user): Add a test emitting an error if "env" is present but it has an invalid value.
  private String env = "standard";
  private ApiConfig apiConfig;
  private final List<String> apiEndpointIds;

  /**
   * Represent user's choice w.r.t the usage of Google's customized connector-j.
   */
  public static enum UseGoogleConnectorJ {
    NOT_STATED_BY_USER,
    TRUE,
    FALSE,
  }
  // Identify if the user has explicitly stated if the application wishes to use Google's
  // customized connector-j.
  private UseGoogleConnectorJ useGoogleConnectorJ = UseGoogleConnectorJ.NOT_STATED_BY_USER;

  public AppEngineWebXml() {
    automaticScaling = new AutomaticScaling();
    manualScaling = new ManualScaling();
    basicScaling = new BasicScaling();
    healthCheck = new HealthCheck();
    resources = new Resources();
    network = new Network();

    staticFileIncludes = new ArrayList<StaticFileInclude>();
    staticFileExcludes = new ArrayList<String>();
    staticFileExcludes.add("WEB-INF/**");
    staticFileExcludes.add("**.jsp");
    resourceFileIncludes = new ArrayList<String>();
    resourceFileExcludes = new ArrayList<String>();
    inboundServices = new LinkedHashSet<String>();
    apiEndpointIds = new ArrayList<String>();
  }

  public AppEngineWebXml clone() {
    try {
      return (AppEngineWebXml) super.clone();
    } catch (CloneNotSupportedException ce) {
      throw new RuntimeException("Could not clone AppEngineWebXml", ce);
    }
  }

  /**
   * @return An unmodifiable map whose entries correspond to the
   * system properties defined in appengine-web.xml.
   */
  public Map<String, String> getSystemProperties() {
    return Collections.unmodifiableMap(systemProperties);
  }

  public void addSystemProperty(String key, String value) {
    systemProperties.put(key, value);
  }

  /**
   * @return An unmodifiable map whose entires correspond to the
   * vm settings defined in appengine-web.xml.
   */
  public Map<String, String> getBetaSettings() {
    return Collections.unmodifiableMap(betaSettings);
  }

  public void addBetaSetting(String key, String value) {
    betaSettings.put(key, value);
  }

  public HealthCheck getHealthCheck() {
    return healthCheck;
  }

  public Resources getResources() {
    return resources;
  }

  public Network getNetwork() {
    return network;
  }

  /**
   * @return An unmodifiable map whose entires correspond to the
   * environment variables defined in appengine-web.xml.
   */
  public Map<String, String> getEnvironmentVariables() {
    return Collections.unmodifiableMap(envVariables);
  }

  public void addEnvironmentVariable(String key, String value) {
    envVariables.put(key, value);
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getMajorVersionId() {
    return majorVersionId;
  }

  public void setMajorVersionId(String majorVersionId) {
    this.majorVersionId = majorVersionId;
  }

  public String getRuntime() {
    if (runtime != null) {
      return runtime;
    }
    // The new env:flex means java, not java7:
    if (isFlexible()) {
      runtime = JAVA_RUNTIME_ID;
    } else {
      runtime = JAVA_7_RUNTIME_ID;
    }
    return runtime;
  }

  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  public String getModule() {
    return module;
  }

  public String getService() {
    return service;
  }

  /**
   * Sets instanceClass (aka class in the xml/yaml files). Normalizes empty and null
   * inputs to null.
   */
  public void setInstanceClass(String instanceClass) {
    this.instanceClass = toNullIfEmptyOrWhitespace(instanceClass);
  }

  public String getInstanceClass() {
    return instanceClass;
  }

  public AutomaticScaling getAutomaticScaling() {
    return automaticScaling;
  }

  public ManualScaling getManualScaling() {
    return manualScaling;
  }

  public BasicScaling getBasicScaling() {
    return basicScaling;
  }

  public ScalingType getScalingType() {
    if (!getBasicScaling().isEmpty()) {
      return ScalingType.BASIC;
    } else if (!getManualScaling().isEmpty()) {
      return ScalingType.MANUAL;
    } else {
      return ScalingType.AUTOMATIC;
    }
  }

  public void setModule(String module) {
    this.module = module;
  }

  public void setService(String service) {
    this.service = service;
  }

  public void setSslEnabled(boolean ssl) {
    sslEnabled = ssl;
  }

  public boolean getSslEnabled() {
    return sslEnabled;
  }

  public void setSessionsEnabled(boolean sessions) {
    useSessions = sessions;
  }

  public boolean getSessionsEnabled() {
    return useSessions;
  }

  public void setAsyncSessionPersistence(boolean asyncSessionPersistence) {
    this.asyncSessionPersistence = asyncSessionPersistence;
  }

  public boolean getAsyncSessionPersistence() {
    return asyncSessionPersistence;
  }

  public void setAsyncSessionPersistenceQueueName(String asyncSessionPersistenceQueueName) {
    this.asyncSessionPersistenceQueueName = asyncSessionPersistenceQueueName;
  }

  public String getAsyncSessionPersistenceQueueName() {
    return asyncSessionPersistenceQueueName;
  }

  public List<StaticFileInclude> getStaticFileIncludes() {
    return staticFileIncludes;
  }

  public List<String> getStaticFileExcludes() {
    return staticFileExcludes;
  }

  public StaticFileInclude includeStaticPattern(String pattern, String expiration) {
    staticIncludePattern = null;
    StaticFileInclude staticFileInclude = new StaticFileInclude(pattern, expiration);
    staticFileIncludes.add(staticFileInclude);
    return staticFileInclude;
  }

  public void excludeStaticPattern(String url) {
    staticExcludePattern = null;
    staticFileExcludes.add(url);
  }

  public List<String> getResourcePatterns() {
    return resourceFileIncludes;
  }

  public List<String> getResourceFileExcludes() {
    return resourceFileExcludes;
  }

  public void includeResourcePattern(String url) {
    resourceExcludePattern = null;
    resourceFileIncludes.add(url);
  }

  public void excludeResourcePattern(String url) {
    resourceIncludePattern = null;
    resourceFileExcludes.add(url);
  }

  public void addUserPermission(String className, String name, String actions) {
    if (className.startsWith("java.")) {
      throw new AppEngineConfigException(
          "Cannot specify user-permissions for " + "classes in java.* packages.");
    }

    userPermissions.add(new UserPermission(className, name, actions));
  }

  public Permissions getUserPermissions() {
    Permissions permissions = new Permissions();
    for (UserPermission permission : userPermissions) {
      permissions.add(
          new UnresolvedPermission(
              permission.getClassName(), permission.getName(), permission.getActions(), null));
    }
    permissions.setReadOnly();
    return permissions;
  }

  public void setPublicRoot(String root) {
    if (root.indexOf('*') != -1) {
      throw new AppEngineConfigException("public-root cannot contain wildcards");
    }
    if (root.endsWith("/")) {
      root = root.substring(0, root.length() - 1);
    }
    if (root.length() > 0 && !root.startsWith("/")) {
      root = "/" + root;
    }
    staticIncludePattern = null;
    publicRoot = root;
  }

  public String getPublicRoot() {
    return publicRoot;
  }

  public void addInboundService(String service) {
    inboundServices.add(service);
  }

  public Set<String> getInboundServices() {
    return inboundServices;
  }

  public boolean getPrecompilationEnabled() {
    return precompilationEnabled;
  }

  public void setPrecompilationEnabled(boolean precompilationEnabled) {
    this.precompilationEnabled = precompilationEnabled;
  }

  public boolean getWarmupRequestsEnabled() {
    return inboundServices.contains(WARMUP_SERVICE);
  }

  public void setWarmupRequestsEnabled(boolean warmupRequestsEnabled) {
    if (warmupRequestsEnabled) {
      inboundServices.add(WARMUP_SERVICE);
    } else {
      inboundServices.remove(WARMUP_SERVICE);
    }
  }

  public List<AdminConsolePage> getAdminConsolePages() {
    return Collections.unmodifiableList(adminConsolePages);
  }

  public void addAdminConsolePage(AdminConsolePage page) {
    adminConsolePages.add(page);
  }

  public List<ErrorHandler> getErrorHandlers() {
    return Collections.unmodifiableList(errorHandlers);
  }

  public void addErrorHandler(ErrorHandler handler) {
    errorHandlers.add(handler);
  }

  public boolean getThreadsafe() {
    return threadsafe;
  }

  public boolean getThreadsafeValueProvided() {
    return threadsafeValueProvided;
  }

  public void setThreadsafe(boolean threadsafe) {
    this.threadsafe = threadsafe;
    this.threadsafeValueProvided = true;
  }

  public void setAutoIdPolicy(String policy) {
    autoIdPolicy = policy;
  }

  public String getAutoIdPolicy() {
    return autoIdPolicy;
  }

  public boolean getCodeLock() {
    return codeLock;
  }

  public void setCodeLock(boolean codeLock) {
    this.codeLock = codeLock;
  }

  public void setUseVm(boolean useVm) {
    this.useVm = useVm;
  }

  public boolean getUseVm() {
    return useVm;
  }

  public void setEnv(String env) {
    this.env = env;
  }

  public String getEnv() {
    return env;
  }

  public boolean isFlexible() {
    return ("flex".equalsIgnoreCase(env) || "2".equals(env) || "flexible".equalsIgnoreCase(env));
  }

  public ApiConfig getApiConfig() {
    return apiConfig;
  }

  public void setApiConfig(ApiConfig config) {
    apiConfig = config;
  }

  public ClassLoaderConfig getClassLoaderConfig() {
    return classLoaderConfig;
  }

  public void setClassLoaderConfig(ClassLoaderConfig classLoaderConfig) {
    if (this.classLoaderConfig != null) {
      throw new AppEngineConfigException("class-loader-config may only be specified once.");
    }
    this.classLoaderConfig = classLoaderConfig;
  }

  public String getUrlStreamHandlerType() {
    return urlStreamHandlerType;
  }

  public void setUrlStreamHandlerType(String urlStreamHandlerType) {
    if (this.classLoaderConfig != null) {
      throw new AppEngineConfigException("url-stream-handler may only be specified once.");
    }
    if (!URL_HANDLER_URLFETCH.equals(urlStreamHandlerType)
        && !URL_HANDLER_NATIVE.equals(urlStreamHandlerType)) {
      throw new AppEngineConfigException(
          "url-stream-handler must be "
              + URL_HANDLER_URLFETCH
              + " or "
              + URL_HANDLER_NATIVE
              + " given "
              + urlStreamHandlerType);
    }
    this.urlStreamHandlerType = urlStreamHandlerType;
  }

  /**
   * Returns true if {@code url} matches one of the servlets or servlet
   * filters listed in this web.xml that has api-endpoint set to true.
   */
  public boolean isApiEndpoint(String id) {
    return apiEndpointIds.contains(id);
  }

  public void addApiEndpoint(String id) {
    apiEndpointIds.add(id);
  }

  public void setUseGoogleConnectorJ(boolean useGoogleConnectorJ) {
    if (useGoogleConnectorJ) {
      this.useGoogleConnectorJ = UseGoogleConnectorJ.TRUE;
    } else {
      this.useGoogleConnectorJ = UseGoogleConnectorJ.FALSE;
    }
  }

  public UseGoogleConnectorJ getUseGoogleConnectorJ() {
    return useGoogleConnectorJ;
  }

  @Override
  public String toString() {
    return "AppEngineWebXml{"
        + "systemProperties="
        + systemProperties
        + ", envVariables="
        + envVariables
        + ", userPermissions="
        + userPermissions
        + ", appId='"
        + appId
        + '\''
        + ", majorVersionId='"
        + majorVersionId
        + '\''
        + ", runtime='"
        + runtime
        + '\''
        + ", service='"
        + service
        + '\''
        + ", instanceClass='"
        + instanceClass
        + '\''
        + ", automaticScaling="
        + automaticScaling
        + ", manualScaling="
        + manualScaling
        + ", basicScaling="
        + basicScaling
        + ", healthCheck="
        + healthCheck
        + ", resources="
        + resources
        + ", network="
        + network
        + ", sslEnabled="
        + sslEnabled
        + ", useSessions="
        + useSessions
        + ", asyncSessionPersistence="
        + asyncSessionPersistence
        + ", asyncSessionPersistenceQueueName='"
        + asyncSessionPersistenceQueueName
        + '\''
        + ", staticFileIncludes="
        + staticFileIncludes
        + ", staticFileExcludes="
        + staticFileExcludes
        + ", resourceFileIncludes="
        + resourceFileIncludes
        + ", resourceFileExcludes="
        + resourceFileExcludes
        + ", staticIncludePattern="
        + staticIncludePattern
        + ", staticExcludePattern="
        + staticExcludePattern
        + ", resourceIncludePattern="
        + resourceIncludePattern
        + ", resourceExcludePattern="
        + resourceExcludePattern
        + ", publicRoot='"
        + publicRoot
        + '\''
        + ", appRoot='"
        + appRoot
        + '\''
        + ", inboundServices="
        + inboundServices
        + ", precompilationEnabled="
        + precompilationEnabled
        + ", adminConsolePages="
        + adminConsolePages
        + ", errorHandlers="
        + errorHandlers
        + ", threadsafe="
        + threadsafe
        + ", threadsafeValueProvided="
        + threadsafeValueProvided
        + ", autoIdPolicy="
        + autoIdPolicy
        + ", codeLock="
        + codeLock
        + ", apiConfig="
        + apiConfig
        + ", apiEndpointIds="
        + apiEndpointIds
        + ", classLoaderConfig="
        + classLoaderConfig
        + ", urlStreamHandlerType="
        + (urlStreamHandlerType == null ? URL_HANDLER_URLFETCH : urlStreamHandlerType)
        + ", useGoogleConnectorJ="
        + useGoogleConnectorJ
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AppEngineWebXml that = (AppEngineWebXml) o;

    if (asyncSessionPersistence != that.asyncSessionPersistence) {
      return false;
    }
    if (precompilationEnabled != that.precompilationEnabled) {
      return false;
    }
    if (sslEnabled != that.sslEnabled) {
      return false;
    }
    if (threadsafe != that.threadsafe) {
      return false;
    }
    if (threadsafeValueProvided != that.threadsafeValueProvided) {
      return false;
    }
    if (autoIdPolicy != null
        ? !autoIdPolicy.equals(that.autoIdPolicy)
        : that.autoIdPolicy != null) {
      return false;
    }
    if (codeLock != that.codeLock) {
      return false;
    }
    if (useSessions != that.useSessions) {
      return false;
    }
    if (adminConsolePages != null
        ? !adminConsolePages.equals(that.adminConsolePages)
        : that.adminConsolePages != null) {
      return false;
    }
    if (appId != null ? !appId.equals(that.appId) : that.appId != null) {
      return false;
    }
    if (majorVersionId != null
        ? !majorVersionId.equals(that.majorVersionId)
        : that.majorVersionId != null) {
      return false;
    }
    if (service != null ? !service.equals(that.service) : that.service != null) {
      return false;
    }
    if (instanceClass != null
        ? !instanceClass.equals(that.instanceClass)
        : that.instanceClass != null) {
      return false;
    }
    if (!automaticScaling.equals(that.automaticScaling)) {
      return false;
    }
    if (!manualScaling.equals(that.manualScaling)) {
      return false;
    }
    if (!basicScaling.equals(that.basicScaling)) {
      return false;
    }
    if (appRoot != null ? !appRoot.equals(that.appRoot) : that.appRoot != null) {
      return false;
    }
    if (asyncSessionPersistenceQueueName != null
        ? !asyncSessionPersistenceQueueName.equals(that.asyncSessionPersistenceQueueName)
        : that.asyncSessionPersistenceQueueName != null) {
      return false;
    }
    if (envVariables != null
        ? !envVariables.equals(that.envVariables)
        : that.envVariables != null) {
      return false;
    }
    if (errorHandlers != null
        ? !errorHandlers.equals(that.errorHandlers)
        : that.errorHandlers != null) {
      return false;
    }
    if (inboundServices != null
        ? !inboundServices.equals(that.inboundServices)
        : that.inboundServices != null) {
      return false;
    }
    if (majorVersionId != null
        ? !majorVersionId.equals(that.majorVersionId)
        : that.majorVersionId != null) {
      return false;
    }
    if (runtime != null
        ? !runtime.equals(that.runtime)
        : that.runtime != null) {
      return false;
    }
    if (publicRoot != null ? !publicRoot.equals(that.publicRoot) : that.publicRoot != null) {
      return false;
    }
    if (resourceExcludePattern != null
        ? !resourceExcludePattern.equals(that.resourceExcludePattern)
        : that.resourceExcludePattern != null) {
      return false;
    }
    if (resourceFileExcludes != null
        ? !resourceFileExcludes.equals(that.resourceFileExcludes)
        : that.resourceFileExcludes != null) {
      return false;
    }
    if (resourceFileIncludes != null
        ? !resourceFileIncludes.equals(that.resourceFileIncludes)
        : that.resourceFileIncludes != null) {
      return false;
    }
    if (resourceIncludePattern != null
        ? !resourceIncludePattern.equals(that.resourceIncludePattern)
        : that.resourceIncludePattern != null) {
      return false;
    }
    if (staticExcludePattern != null
        ? !staticExcludePattern.equals(that.staticExcludePattern)
        : that.staticExcludePattern != null) {
      return false;
    }
    if (staticFileExcludes != null
        ? !staticFileExcludes.equals(that.staticFileExcludes)
        : that.staticFileExcludes != null) {
      return false;
    }
    if (staticFileIncludes != null
        ? !staticFileIncludes.equals(that.staticFileIncludes)
        : that.staticFileIncludes != null) {
      return false;
    }
    if (staticIncludePattern != null
        ? !staticIncludePattern.equals(that.staticIncludePattern)
        : that.staticIncludePattern != null) {
      return false;
    }
    if (systemProperties != null
        ? !systemProperties.equals(that.systemProperties)
        : that.systemProperties != null) {
      return false;
    }
    if (betaSettings != null
        ? !betaSettings.equals(that.betaSettings)
        : that.betaSettings != null) {
      return false;
    }
    if (healthCheck != null ? !healthCheck.equals(that.healthCheck) : that.healthCheck != null) {
      return false;
    }
    if (resources != null ? !resources.equals(that.resources) : that.resources != null) {
      return false;
    }
    if (network != null ? !network.equals(that.network) : that.network != null) {
      return false;
    }
    if (userPermissions != null
        ? !userPermissions.equals(that.userPermissions)
        : that.userPermissions != null) {
      return false;
    }
    if (apiConfig != null ? !apiConfig.equals(that.apiConfig) : that.apiConfig != null) {
      return false;
    }
    if (apiEndpointIds != null
        ? !apiEndpointIds.equals(that.apiEndpointIds)
        : that.apiEndpointIds != null) {
      return false;
    }
    if (classLoaderConfig != null
        ? !classLoaderConfig.equals(that.classLoaderConfig)
        : that.classLoaderConfig != null) {
      return false;
    }
    if (urlStreamHandlerType != null
        ? !urlStreamHandlerType.equals(that.urlStreamHandlerType)
        : that.urlStreamHandlerType != null) {
      return false;
    }
    if (useGoogleConnectorJ != that.useGoogleConnectorJ) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = systemProperties != null ? systemProperties.hashCode() : 0;
    result = 31 * result + (envVariables != null ? envVariables.hashCode() : 0);
    result = 31 * result + (userPermissions != null ? userPermissions.hashCode() : 0);
    result = 31 * result + (appId != null ? appId.hashCode() : 0);
    result = 31 * result + (majorVersionId != null ? majorVersionId.hashCode() : 0);
    result = 31 * result + (runtime != null ? runtime.hashCode() : 0);
    result = 31 * result + (service != null ? service.hashCode() : 0);
    result = 31 * result + (instanceClass != null ? instanceClass.hashCode() : 0);
    result = 31 * result + automaticScaling.hashCode();
    result = 31 * result + manualScaling.hashCode();
    result = 31 * result + basicScaling.hashCode();
    result = 31 * result + (sslEnabled ? 1 : 0);
    result = 31 * result + (useSessions ? 1 : 0);
    result = 31 * result + (asyncSessionPersistence ? 1 : 0);
    result =
        31 * result
            + (asyncSessionPersistenceQueueName != null
                ? asyncSessionPersistenceQueueName.hashCode()
                : 0);
    result = 31 * result + (staticFileIncludes != null ? staticFileIncludes.hashCode() : 0);
    result = 31 * result + (staticFileExcludes != null ? staticFileExcludes.hashCode() : 0);
    result = 31 * result + (resourceFileIncludes != null ? resourceFileIncludes.hashCode() : 0);
    result = 31 * result + (resourceFileExcludes != null ? resourceFileExcludes.hashCode() : 0);
    result = 31 * result + (staticIncludePattern != null ? staticIncludePattern.hashCode() : 0);
    result = 31 * result + (staticExcludePattern != null ? staticExcludePattern.hashCode() : 0);
    result = 31 * result + (resourceIncludePattern != null ? resourceIncludePattern.hashCode() : 0);
    result = 31 * result + (resourceExcludePattern != null ? resourceExcludePattern.hashCode() : 0);
    result = 31 * result + (publicRoot != null ? publicRoot.hashCode() : 0);
    result = 31 * result + (appRoot != null ? appRoot.hashCode() : 0);
    result = 31 * result + (inboundServices != null ? inboundServices.hashCode() : 0);
    result = 31 * result + (precompilationEnabled ? 1 : 0);
    result = 31 * result + (adminConsolePages != null ? adminConsolePages.hashCode() : 0);
    result = 31 * result + (errorHandlers != null ? errorHandlers.hashCode() : 0);
    result = 31 * result + (threadsafe ? 1 : 0);
    result = 31 * result + (autoIdPolicy != null ? autoIdPolicy.hashCode() : 0);
    result = 31 * result + (threadsafeValueProvided ? 1 : 0);
    result = 31 * result + (codeLock ? 1 : 0);
    result = 31 * result + (apiConfig != null ? apiConfig.hashCode() : 0);
    result = 31 * result + (apiEndpointIds != null ? apiEndpointIds.hashCode() : 0);
    result = 31 * result + (classLoaderConfig != null ? classLoaderConfig.hashCode() : 0);
    result = 31 * result + (urlStreamHandlerType != null ? urlStreamHandlerType.hashCode() : 0);
    result = 31 * result + (useGoogleConnectorJ.hashCode());
    result = 31 * result + (betaSettings != null ? betaSettings.hashCode() : 0);
    result = 31 * result + (healthCheck != null ? healthCheck.hashCode() : 0);
    result = 31 * result + (resources != null ? resources.hashCode() : 0);
    result = 31 * result + (network != null ? network.hashCode() : 0);
    return result;
  }

  public boolean includesResource(String path) {
    if (resourceIncludePattern == null) {
      if (resourceFileIncludes.size() == 0) {
        // if the user doesn't give any includes, we want everything
        resourceIncludePattern = Pattern.compile(".*");
      } else {
        resourceIncludePattern = Pattern.compile(makeRegexp(resourceFileIncludes));
      }
    }
    if (resourceExcludePattern == null && resourceFileExcludes.size() > 0) {
      resourceExcludePattern = Pattern.compile(makeRegexp(resourceFileExcludes));
    } else {
      // if there are no resourceFileExcludes, let the pattern stay NULL.
    }
    return includes(path, resourceIncludePattern, resourceExcludePattern);
  }

  public boolean includesStatic(String path) {
    if (staticIncludePattern == null) {
      if (staticFileIncludes.size() == 0) {
        // if the user doesn't give any includes, we want everything under
        // publicRoot
        String staticRoot;
        if (publicRoot.length() > 0) {
          staticRoot = publicRoot + "/**";
        } else {
          staticRoot = "**";
        }
        staticIncludePattern = Pattern.compile(makeRegexp(Collections.singletonList(staticRoot)));
      } else {
        List<String> patterns = new ArrayList<String>();
        for (StaticFileInclude include : staticFileIncludes) {
          patterns.add(include.getPattern());
        }
        staticIncludePattern = Pattern.compile(makeRegexp(patterns));
      }
    }
    if (staticExcludePattern == null && staticFileExcludes.size() > 0) {
      staticExcludePattern = Pattern.compile(makeRegexp(staticFileExcludes));
    } else {
      // if there are no staticFileExcludes, let the pattern stay NULL.
    }
    return includes(path, staticIncludePattern, staticExcludePattern);
  }

  /**
   * Tests whether {@code path} is covered by the pattern {@code includes}
   * while not being blocked by matching {@code excludes}.
   *
   * @param path a URL to test
   * @param includes a non-{@code null} pattern for included URLs
   * @param excludes a pattern for exclusion, or {@code null} to not exclude
   *    anything from the {@code includes} set.
   */
  public boolean includes(String path, Pattern includes, Pattern excludes) {
    assert (includes != null);
    if (!includes.matcher(path).matches()) {
      return false;
    }
    if (excludes != null && excludes.matcher(path).matches()) {
      return false;
    }
    return true;
  }

  public String makeRegexp(List<String> patterns) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String item : patterns) {
      if (first) {
        first = false;
      } else {
        builder.append('|');
      }

      // Trim any leading slashes from item.
      while (item.charAt(0) == '/') {
        item = item.substring(1);
      }

      builder.append('(');
      if (appRoot != null) {
        builder.append(makeFileRegex(appRoot));
      }
      builder.append("/");
      builder.append(makeFileRegex(item));
      builder.append(')');
    }
    return builder.toString();
  }

  /**
   * Helper method to translate from appengine-web.xml "file globs" to
   * proper regular expressions as used in app.yaml.
   *
   * @param fileGlob the glob to translate
   * @return the regular expression string matching the input {@code file} pattern.
   */
  static String makeFileRegex(String fileGlob) {
    // escape metacharacters, and replace '*' with regexp '[^/]*' and '**' with '.*'
    fileGlob = fileGlob.replaceAll("([^A-Za-z0-9\\-_/])", "\\\\$1");
    fileGlob = fileGlob.replaceAll("\\\\\\*\\\\\\*", ".*");
    fileGlob = fileGlob.replaceAll("\\\\\\*", "[^/]*");
    return fileGlob;
  }
  /**
   * Sets the application root directory, as a prefix for the regexps in
   * {@link #includeResourcePattern(String)} and friends.  This is needed
   * because we want to match complete filenames relative to root.
   */
  public void setSourcePrefix(String appRoot) {
    this.appRoot = appRoot;
    // Invalidate the pattern cache because the patterns
    // were generated with the previous root.
    this.resourceIncludePattern = null;
    this.resourceExcludePattern = null;
    this.staticIncludePattern = null;
    this.staticExcludePattern = null;
  }

  public String getSourcePrefix() {
    return this.appRoot;
  }

  private static String toNullIfEmptyOrWhitespace(String string) {
    if (string == null || CharMatcher.whitespace().matchesAllOf(string)) {
      return null;
    }
    return string;
  }

  /**
   * Represents a {@link java.security.Permission} that needs to be
   * granted to user code.
   */
  private static class UserPermission {
    private final String className;
    private final String name;
    private final String actions;

    private boolean hasHashCode = false;
    private int hashCode;

    public UserPermission(String className, String name, String actions) {
      this.className = className;
      this.name = name;
      this.actions = actions;
    }

    public String getClassName() {
      return className;
    }

    public String getName() {
      return name;
    }

    public String getActions() {
      return actions;
    }

    @Override
    public int hashCode() {
      if (hasHashCode) {
        return hashCode;
      }

      int hash = className.hashCode();
      hash = 31 * hash + name.hashCode();
      if (actions != null) {
        hash = 31 * hash + actions.hashCode();
      }

      hashCode = hash;
      hasHashCode = true;
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof UserPermission) {
        UserPermission perm = (UserPermission) obj;
        if (className.equals(perm.className) && name.equals(perm.name)) {
          if (actions == null ? perm.actions == null : actions.equals(perm.actions)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /**
   * Represents an &lt;include&gt; element within the
   * &lt;static-files&gt; element.  Currently this includes both a
   * pattern and an optional expiration time specification.
   */
  public static class StaticFileInclude {
    private final String pattern;
    private final String expiration;
    private final Map<String, String> httpHeaders;

    public StaticFileInclude(String pattern, String expiration) {
      this.pattern = pattern;
      this.expiration = expiration;
      this.httpHeaders = new LinkedHashMap<>();
    }

    public String getPattern() {
      return pattern;
    }

    public Pattern getRegularExpression() {
      return Pattern.compile(makeFileRegex(pattern));
    }

    public String getExpiration() {
      return expiration;
    }

    public Map<String, String> getHttpHeaders() {
      return httpHeaders;
    }

    @Override
    public int hashCode() {
      return Objects.hash(pattern, expiration, httpHeaders);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof StaticFileInclude)) {
        return false;
      }

      StaticFileInclude other = (StaticFileInclude) obj;

      // If patterns differ, return false.
      if (pattern != null) {
        if (!pattern.equals(other.pattern)) {
          return false;
        }
      } else { // pattern == null
        if (other.pattern != null) {
          return false;
        }
        // else, both patterns null; thus, they do not differ.
      }

      // If expirations differ, return false.
      if (expiration != null) {
        if (!expiration.equals(other.expiration)) {
          return false;
        }
      } else { // expiration == null
        if (other.expiration != null) {
          return false;
        }
        // else, both expirations null; thus, they do not differ.
      }

      // If httpHeaders differ, return false.
      if (httpHeaders != null) {
        if (!httpHeaders.equals(other.httpHeaders)) {
          return false;
        }
      } else { // httpHeaders == null
        if (other.httpHeaders != null) {
          return false;
        }
        // else, both httpHeaders null; thus, they do not differ.
      }

      // All attributes are equal; thus, this equals other.
      return true;
    }
  }

  public static class AdminConsolePage {
    private final String name;
    private final String url;

    public AdminConsolePage(String name, String url) {
      this.name = name;
      this.url = url;
    }

    public String getName() {
      return name;
    }

    public String getUrl() {
      return url;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((url == null) ? 0 : url.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      AdminConsolePage other = (AdminConsolePage) obj;
      if (name == null) {
        if (other.name != null) {
          return false;
        }
      } else if (!name.equals(other.name)) {
        return false;
      }
      if (url == null) {
        if (other.url != null) {
          return false;
        }
      } else if (!url.equals(other.url)) {
        return false;
      }
      return true;
    }
  }

  /**
   * Represents an &lt;error-handler&gt; element.  Currently this includes both
   * a file name and an optional error code.
   */
  public static class ErrorHandler {
    private final String file;
    private final String errorCode;

    public ErrorHandler(String file, String errorCode) {
      this.file = file;
      this.errorCode = errorCode;
    }

    public String getFile() {
      return file;
    }

    public String getErrorCode() {
      return errorCode;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((file == null) ? 0 : file.hashCode());
      result = prime * result + ((errorCode == null) ? 0 : errorCode.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ErrorHandler)) {
        return false;
      }

      ErrorHandler handler = (ErrorHandler) obj;
      if (!file.equals(handler.file)) {
        return false;
      }

      if (errorCode == null) {
        if (handler.errorCode != null) {
          return false;
        }
      } else {
        if (!errorCode.equals(handler.errorCode)) {
          return false;
        }
      }

      return true;
    }
  }

  /**
   * Represents an &lt;api-config&gt; element.  This is a singleton specifying
   * url-pattern and servlet-class for the api config server.
   */
  public static class ApiConfig {
    private final String servletClass;
    private final String url;

    public ApiConfig(String servletClass, String url) {
      this.servletClass = servletClass;
      this.url = url;
    }

    public String getservletClass() {
      return servletClass;
    }

    public String getUrl() {
      return url;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((servletClass == null) ? 0 : servletClass.hashCode());
      result = prime * result + ((url == null) ? 0 : url.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ApiConfig other = (ApiConfig) obj;
      if (servletClass == null) {
        if (other.servletClass != null) {
          return false;
        }
      } else if (!servletClass.equals(other.servletClass)) {
        return false;
      }
      if (url == null) {
        if (other.url != null) {
          return false;
        }
      } else if (!url.equals(other.url)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "ApiConfig{servletClass=\"" + servletClass + "\", url=\"" + url + "\"}";
    }
  }

  /**
   * Holder for automatic settings.
   */
  public static class AutomaticScaling {
    /*
     * AutomaticScaling with no fields set.
     *
     * Keep this private because AutomaticScaling is mutable.
     */
    private static final AutomaticScaling EMPTY_SETTINGS = new AutomaticScaling();

    public static final String AUTOMATIC = "automatic";
    private String minPendingLatency;
    private String maxPendingLatency;
    private String minIdleInstances;
    private String maxIdleInstances;
    private String maxConcurrentRequests;

    private Integer minNumInstances;
    private Integer maxNumInstances;
    private Integer coolDownPeriodSec;
    private CpuUtilization cpuUtilization;

    private Integer targetNetworkSentBytesPerSec;
    private Integer targetNetworkSentPacketsPerSec;
    private Integer targetNetworkReceivedBytesPerSec;
    private Integer targetNetworkReceivedPacketsPerSec;
    private Integer targetDiskWriteBytesPerSec;
    private Integer targetDiskWriteOpsPerSec;
    private Integer targetDiskReadBytesPerSec;
    private Integer targetDiskReadOpsPerSec;
    private Integer targetRequestCountPerSec;
    private Integer targetConcurrentRequests;

    public String getMinPendingLatency() {
      return minPendingLatency;
    }

    /**
     * Sets minPendingLatency. Normalizes empty and null inputs to null.
     */
    public void setMinPendingLatency(String minPendingLatency) {
      this.minPendingLatency = toNullIfEmptyOrWhitespace(minPendingLatency);
    }

    public String getMaxPendingLatency() {
      return maxPendingLatency;
    }

    /**
     * Sets maxPendingLatency. Normalizes empty and null inputs to null.
     */
    public void setMaxPendingLatency(String maxPendingLatency) {
      this.maxPendingLatency = toNullIfEmptyOrWhitespace(maxPendingLatency);
    }

    public String getMinIdleInstances() {
      return minIdleInstances;
    }

    /**
     * Sets minIdleInstances. Normalizes empty and null inputs to null.
     */
    public void setMinIdleInstances(String minIdleInstances) {
      this.minIdleInstances = toNullIfEmptyOrWhitespace(minIdleInstances);
    }

    public String getMaxIdleInstances() {
      return maxIdleInstances;
    }

    /**
     * Sets maxIdleInstances. Normalizes empty and null inputs to null.
     */
    public void setMaxIdleInstances(String maxIdleInstances) {
      this.maxIdleInstances = toNullIfEmptyOrWhitespace(maxIdleInstances);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    public String getMaxConcurrentRequests() {
      return maxConcurrentRequests;
    }

    /**
     * Sets maxConcurrentRequests. Normalizes empty and null inputs to null.
     */
    public void setMaxConcurrentRequests(String maxConcurrentRequests) {
      this.maxConcurrentRequests = toNullIfEmptyOrWhitespace(maxConcurrentRequests);
    }

    public Integer getMinNumInstances() {
      return minNumInstances;
    }

    public void setMinNumInstances(Integer minNumInstances) {
      this.minNumInstances = minNumInstances;
    }

    public Integer getMaxNumInstances() {
      return maxNumInstances;
    }

    public void setMaxNumInstances(Integer maxNumInstances) {
      this.maxNumInstances = maxNumInstances;
    }

    public Integer getCoolDownPeriodSec() {
      return coolDownPeriodSec;
    }

    public void setCoolDownPeriodSec(Integer coolDownPeriodSec) {
      this.coolDownPeriodSec = coolDownPeriodSec;
    }

    public CpuUtilization getCpuUtilization() {
      return cpuUtilization;
    }

    public void setCpuUtilization(CpuUtilization cpuUtilization) {
      this.cpuUtilization = cpuUtilization;
    }

    public Integer getTargetNetworkSentBytesPerSec() {
      return targetNetworkSentBytesPerSec;
    }

    public void setTargetNetworkSentBytesPerSec(Integer targetNetworkSentBytesPerSec) {
      this.targetNetworkSentBytesPerSec = targetNetworkSentBytesPerSec;
    }

    public Integer getTargetNetworkSentPacketsPerSec() {
      return targetNetworkSentPacketsPerSec;
    }

    public void setTargetNetworkSentPacketsPerSec(Integer targetNetworkSentPacketsPerSec) {
      this.targetNetworkSentPacketsPerSec = targetNetworkSentPacketsPerSec;
    }

    public Integer getTargetNetworkReceivedBytesPerSec() {
      return targetNetworkReceivedBytesPerSec;
    }

    public void setTargetNetworkReceivedBytesPerSec(Integer targetNetworkReceivedBytesPerSec) {
      this.targetNetworkReceivedBytesPerSec = targetNetworkReceivedBytesPerSec;
    }

    public Integer getTargetNetworkReceivedPacketsPerSec() {
      return targetNetworkReceivedPacketsPerSec;
    }

    public void setTargetNetworkReceivedPacketsPerSec(Integer targetNetworkReceivedPacketsPerSec) {
      this.targetNetworkReceivedPacketsPerSec = targetNetworkReceivedPacketsPerSec;
    }

    public Integer getTargetDiskWriteBytesPerSec() {
      return targetDiskWriteBytesPerSec;
    }

    public void setTargetDiskWriteBytesPerSec(Integer targetDiskWriteBytesPerSec) {
      this.targetDiskWriteBytesPerSec = targetDiskWriteBytesPerSec;
    }

    public Integer getTargetDiskWriteOpsPerSec() {
      return targetDiskWriteOpsPerSec;
    }

    public void setTargetDiskWriteOpsPerSec(Integer targetDiskWriteOpsPerSec) {
      this.targetDiskWriteOpsPerSec = targetDiskWriteOpsPerSec;
    }

    public Integer getTargetDiskReadBytesPerSec() {
      return targetDiskReadBytesPerSec;
    }

    public void setTargetDiskReadBytesPerSec(Integer targetDiskReadBytesPerSec) {
      this.targetDiskReadBytesPerSec = targetDiskReadBytesPerSec;
    }

    public Integer getTargetDiskReadOpsPerSec() {
      return targetDiskReadOpsPerSec;
    }

    public void setTargetDiskReadOpsPerSec(Integer targetDiskReadOpsPerSec) {
      this.targetDiskReadOpsPerSec = targetDiskReadOpsPerSec;
    }

    public Integer getTargetRequestCountPerSec() {
      return targetRequestCountPerSec;
    }

    public void setTargetRequestCountPerSec(Integer targetRequestCountPerSec) {
      this.targetRequestCountPerSec = targetRequestCountPerSec;
    }

    public Integer getTargetConcurrentRequests() {
      return targetConcurrentRequests;
    }

    public void setTargetConcurrentRequests(Integer targetConcurrentRequests) {
      this.targetConcurrentRequests = targetConcurrentRequests;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          maxPendingLatency,
          minPendingLatency,
          maxIdleInstances,
          minIdleInstances,
          maxConcurrentRequests,
          minNumInstances,
          maxNumInstances,
          coolDownPeriodSec,
          cpuUtilization,
          targetNetworkSentBytesPerSec,
          targetNetworkSentPacketsPerSec,
          targetNetworkReceivedBytesPerSec,
          targetNetworkReceivedPacketsPerSec,
          targetDiskWriteBytesPerSec,
          targetDiskWriteOpsPerSec,
          targetDiskReadBytesPerSec,
          targetDiskReadOpsPerSec,
          targetRequestCountPerSec,
          targetConcurrentRequests);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      AutomaticScaling other = (AutomaticScaling) obj;
      return Objects.equals(maxPendingLatency, other.maxPendingLatency)
          && Objects.equals(minPendingLatency, other.minPendingLatency)
          && Objects.equals(maxIdleInstances, other.maxIdleInstances)
          && Objects.equals(minIdleInstances, other.minIdleInstances)
          && Objects.equals(maxConcurrentRequests, other.maxConcurrentRequests)
          && Objects.equals(minNumInstances, other.minNumInstances)
          && Objects.equals(maxNumInstances, other.maxNumInstances)
          && Objects.equals(coolDownPeriodSec, other.coolDownPeriodSec)
          && Objects.equals(cpuUtilization, other.cpuUtilization)
          && Objects.equals(targetNetworkSentBytesPerSec, other.targetNetworkSentBytesPerSec)
          && Objects.equals(targetNetworkSentPacketsPerSec, other.targetNetworkSentPacketsPerSec)
          && Objects.equals(
              targetNetworkReceivedBytesPerSec, other.targetNetworkReceivedBytesPerSec)
          && Objects.equals(
              targetNetworkReceivedPacketsPerSec, other.targetNetworkReceivedPacketsPerSec)
          && Objects.equals(targetDiskWriteBytesPerSec, other.targetDiskWriteBytesPerSec)
          && Objects.equals(targetDiskWriteOpsPerSec, other.targetDiskWriteOpsPerSec)
          && Objects.equals(targetDiskReadBytesPerSec, other.targetDiskReadBytesPerSec)
          && Objects.equals(targetDiskReadOpsPerSec, other.targetDiskReadOpsPerSec)
          && Objects.equals(targetRequestCountPerSec, other.targetRequestCountPerSec)
          && Objects.equals(targetConcurrentRequests, other.targetConcurrentRequests);
    }

    @Override
    public String toString() {
      return "AutomaticScaling [minPendingLatency="
          + minPendingLatency
          + ", maxPendingLatency="
          + maxPendingLatency
          + ", minIdleInstances="
          + minIdleInstances
          + ", maxIdleInstances="
          + maxIdleInstances
          + ", maxConcurrentRequests="
          + maxConcurrentRequests
          + ", minNumInstances="
          + minNumInstances
          + ", maxNumInstances="
          + maxNumInstances
          + ", coolDownPeriodSec="
          + coolDownPeriodSec
          + ", cpuUtilization="
          + cpuUtilization
          + ", targetNetworkSentBytesPerSec="
          + targetNetworkSentBytesPerSec
          + ", targetNetworkSentPacketsPerSec="
          + targetNetworkSentPacketsPerSec
          + ", targetNetworkReceivedBytesPerSec="
          + targetNetworkReceivedBytesPerSec
          + ", targetNetworkReceivedPacketsPerSec="
          + targetNetworkReceivedPacketsPerSec
          + ", targetDiskWriteBytesPerSec="
          + targetDiskWriteBytesPerSec
          + ", targetDiskWriteOpsPerSec="
          + targetDiskWriteOpsPerSec
          + ", targetDiskReadBytesPerSec="
          + targetDiskReadBytesPerSec
          + ", targetDiskReadOpsPerSec="
          + targetDiskReadOpsPerSec
          + ", targetRequestCountPerSec="
          + targetRequestCountPerSec
          + ", targetConcurrentRequests="
          + targetConcurrentRequests
          + "]";
    }
  }

  /**
   * Holder for CPU utilization.
   */
  public static class CpuUtilization {
    private static final CpuUtilization EMPTY_SETTINGS = new CpuUtilization();
    // The target of CPU utilization.
    private Double targetUtilization;
    // The number of seconds used to aggregate CPU usage.
    private Integer aggregationWindowLengthSec;

    public Double getTargetUtilization() {
      return targetUtilization;
    }

    public void setTargetUtilization(Double targetUtilization) {
      this.targetUtilization = targetUtilization;
    }

    public Integer getAggregationWindowLengthSec() {
      return aggregationWindowLengthSec;
    }

    public void setAggregationWindowLengthSec(Integer aggregationWindowLengthSec) {
      this.aggregationWindowLengthSec = aggregationWindowLengthSec;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetUtilization, aggregationWindowLengthSec);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      CpuUtilization other = (CpuUtilization) obj;
      return Objects.equals(targetUtilization, other.targetUtilization)
          && Objects.equals(aggregationWindowLengthSec, other.aggregationWindowLengthSec);
    }

    @Override
    public String toString() {
      return "CpuUtilization [targetUtilization="
          + targetUtilization
          + ", aggregationWindowLengthSec="
          + aggregationWindowLengthSec
          + "]";
    }
  }

  /**
   * Holder for health check.
   */
  public static class HealthCheck {
    /*
     * HealthCheck with no fields set.
     *
     * Keep this private because HealthCheck is mutable.
     */
    private static final HealthCheck EMPTY_SETTINGS = new HealthCheck();

    // Health check is enabled by default.
    private boolean enableHealthCheck = true;
    private Integer checkIntervalSec;
    private Integer timeoutSec;
    private Integer unhealthyThreshold;
    private Integer healthyThreshold;
    private Integer restartThreshold;
    private String host;

    public boolean getEnableHealthCheck() {
      return enableHealthCheck;
    }
    /**
     * Sets enableHealthCheck.
     */
    public void setEnableHealthCheck(boolean enableHealthCheck) {
      this.enableHealthCheck = enableHealthCheck;
    }

    public Integer getCheckIntervalSec() {
      return checkIntervalSec;
    }
    /**
     * Sets checkIntervalSec.
     */
    public void setCheckIntervalSec(Integer checkIntervalSec) {
      this.checkIntervalSec = checkIntervalSec;
    }

    public Integer getTimeoutSec() {
      return timeoutSec;
    }
    /**
     * Sets timeoutSec.
     */
    public void setTimeoutSec(Integer timeoutSec) {
      this.timeoutSec = timeoutSec;
    }

    public Integer getUnhealthyThreshold() {
      return unhealthyThreshold;
    }

    /**
     * Sets unhealthyThreshold.
     */
    public void setUnhealthyThreshold(Integer unhealthyThreshold) {
      this.unhealthyThreshold = unhealthyThreshold;
    }

    public Integer getHealthyThreshold() {
      return healthyThreshold;
    }

    /**
     * Sets healthyThreshold.
     */
    public void setHealthyThreshold(Integer healthyThreshold) {
      this.healthyThreshold = healthyThreshold;
    }

    public Integer getRestartThreshold() {
      return restartThreshold;
    }

    /**
     * Sets restartThreshold.
     */
    public void setRestartThreshold(Integer restartThreshold) {
      this.restartThreshold = restartThreshold;
    }

    public String getHost() {
      return host;
    }

    /**
     * Sets host. Normalizes empty and null inputs to null.
     */
    public void setHost(String host) {
      this.host = toNullIfEmptyOrWhitespace(host);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          enableHealthCheck,
          checkIntervalSec,
          timeoutSec,
          unhealthyThreshold,
          healthyThreshold,
          restartThreshold,
          host);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      HealthCheck other = (HealthCheck) obj;
      return Objects.equals(enableHealthCheck, other.enableHealthCheck)
          && Objects.equals(checkIntervalSec, other.checkIntervalSec)
          && Objects.equals(timeoutSec, other.timeoutSec)
          && Objects.equals(unhealthyThreshold, other.unhealthyThreshold)
          && Objects.equals(healthyThreshold, other.healthyThreshold)
          && Objects.equals(restartThreshold, other.restartThreshold)
          && Objects.equals(host, other.host);
    }

    @Override
    public String toString() {
      return "HealthCheck [enableHealthCheck="
          + enableHealthCheck
          + ", checkIntervalSec="
          + checkIntervalSec
          + ", timeoutSec="
          + timeoutSec
          + ", unhealthyThreshold="
          + unhealthyThreshold
          + ", healthyThreshold="
          + healthyThreshold
          + ", restartThreshold="
          + restartThreshold
          + ", host="
          + host
          + "]";
    }
  }

  /**
   * Holder for Resources
   */
  public static class Resources {
    /*
     * Resources with no fields set.
     *
     * Keep this private because Resources is mutable.
     */
    private static final Resources EMPTY_SETTINGS = new Resources();

    private double cpu;

    public double getCpu() {
      return cpu;
    }

    public void setCpu(double cpu) {
      this.cpu = cpu;
    }

    private double memoryGb;

    public double getMemoryGb() {
      return memoryGb;
    }

    public void setMemoryGb(double memoryGb) {
      this.memoryGb = memoryGb;
    }

    private int diskSizeGb;

    public int getDiskSizeGb() {
      return diskSizeGb;
    }

    public void setDiskSizeGb(int diskSizeGb) {
      this.diskSizeGb = diskSizeGb;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(cpu, memoryGb, diskSizeGb);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Resources other = (Resources) obj;
      return Objects.equals(cpu, other.cpu)
          && Objects.equals(memoryGb, other.memoryGb)
          && Objects.equals(diskSizeGb, other.diskSizeGb);
    }

    @Override
    public String toString() {
      return "Resources ["
          + "cpu="
          + cpu
          + ", memoryGb="
          + memoryGb
          + ", diskSizeGb="
          + diskSizeGb
          + "]";
    }
  }

  /**
   * Holder for network.
   */
  public static class Network {
    /*
     * Network with no fields set.
     *
     * Keep this private because Network is mutable.
     */
    private static final Network EMPTY_SETTINGS = new Network();

    private String instanceTag;

    public String getInstanceTag() {
      return instanceTag;
    }

    public void setInstanceTag(String instanceTag) {
      this.instanceTag = instanceTag;
    }

    private final List<String> forwardedPorts = Lists.newArrayList();

    public List<String> getForwardedPorts() {
      return Collections.unmodifiableList(forwardedPorts);
    }

    public void addForwardedPort(String forwardedPort) {
      forwardedPorts.add(forwardedPort);
    }

    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(forwardedPorts, instanceTag);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Network other = (Network) obj;
      return Objects.equals(forwardedPorts, other.forwardedPorts)
          && Objects.equals(instanceTag, other.instanceTag);
    }

    @Override
    public String toString() {
      return "Network [forwardedPorts=" + forwardedPorts + ", instanceTag=" + instanceTag + "]";
    }
  }

  /**
   * Holder for manual settings.
   */
  public static class ManualScaling {
    /*
     * ManualScaling with no fields set.
     *
     * Keep this private because ManualScaling is mutable.
     */
    private static final ManualScaling EMPTY_SETTINGS = new ManualScaling();

    private String instances;

    public String getInstances() {
      return instances;
    }

    /**
     * Sets instances. Normalizes empty and null inputs to null.
     */
    public void setInstances(String instances) {
      this.instances = toNullIfEmptyOrWhitespace(instances);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(instances);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ManualScaling other = (ManualScaling) obj;
      return Objects.equals(instances, other.instances);
    }

    @Override
    public String toString() {
      return "ManualScaling [" + "instances=" + instances + "]";
    }
  }

  /**
   * Holder for basic settings.
   */
  public static class BasicScaling {
    /*
     * BasicScaling with no fields set.
     *
     * Keep this private because BasicScaling is mutable.
     */
    private static final BasicScaling EMPTY_SETTINGS = new BasicScaling();

    private String maxInstances;
    private String idleTimeout;

    public String getMaxInstances() {
      return maxInstances;
    }

    public String getIdleTimeout() {
      return idleTimeout;
    }

    /**
     * Sets maxInstances. Normalizes empty and null inputs to null.
     */
    public void setMaxInstances(String maxInstances) {
      this.maxInstances = toNullIfEmptyOrWhitespace(maxInstances);
    }

    /**
     * Sets idleTimeout. Normalizes empty and null inputs to null.
     */
    public void setIdleTimeout(String idleTimeout) {
      this.idleTimeout = toNullIfEmptyOrWhitespace(idleTimeout);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(maxInstances, idleTimeout);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      BasicScaling other = (BasicScaling) obj;
      return Objects.equals(maxInstances, other.maxInstances)
          && Objects.equals(idleTimeout, other.idleTimeout);
    }

    @Override
    public String toString() {
      return "BasicScaling ["
          + "maxInstances="
          + maxInstances
          + ", idleTimeout="
          + idleTimeout
          + "]";
    }
  }

  public static class ClassLoaderConfig {
    private final List<PrioritySpecifierEntry> entries = Lists.newArrayList();

    public void add(PrioritySpecifierEntry entry) {
      entries.add(entry);
    }

    public List<PrioritySpecifierEntry> getEntries() {
      return entries;
    }

    // Generated by eclipse.
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((entries == null) ? 0 : entries.hashCode());
      return result;
    }

    // Generated by eclipse.
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ClassLoaderConfig other = (ClassLoaderConfig) obj;
      if (entries == null) {
        if (other.entries != null) {
          return false;
        }
      } else if (!entries.equals(other.entries)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "ClassLoaderConfig{entries=\"" + entries + "\"}";
    }
  }

  public static class PrioritySpecifierEntry {
    private String filename;
    private Double priority; // null means not present.  Default priority is 1.0.

    private void checkNotAlreadySet() {
      if (filename != null) {
        throw new AppEngineConfigException(
            "Found more that one file name matching tag. "
                + "Only one of 'filename' attribute allowed.");
      }
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      checkNotAlreadySet();
      this.filename = filename;
    }

    public Double getPriority() {
      return priority;
    }

    // Returns the priority of this specifier or the assumed default priority
    // value if it is not specified.
    public double getPriorityValue() {
      if (priority == null) {
        return 1.0d;
      }
      return priority;
    }

    public void setPriority(String priority) {
      if (this.priority != null) {
        throw new AppEngineConfigException("The 'priority' tag may only be specified once.");
      }

      if (priority == null) {
        this.priority = null;
        return;
      }

      this.priority = Double.parseDouble(priority);
    }

    // Check that this is a valid ClassLoaderConfig
    public void checkClassLoaderConfig() {
      if (filename == null) {
        throw new AppEngineConfigException("Must have a filename attribute.");
      }
    }

    // Generated by eclipse.
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((filename == null) ? 0 : filename.hashCode());
      result = prime * result + ((priority == null) ? 0 : priority.hashCode());
      return result;
    }

    // Generated by eclipse.
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      PrioritySpecifierEntry other = (PrioritySpecifierEntry) obj;
      if (filename == null) {
        if (other.filename != null) {
          return false;
        }
      } else if (!filename.equals(other.filename)) {
        return false;
      }
      if (priority == null) {
        if (other.priority != null) {
          return false;
        }
      } else if (!priority.equals(other.priority)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "PrioritySpecifierEntry{filename=\"" + filename + "\", priority=\"" + priority + "\"}";
    }
  }
}

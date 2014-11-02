/**
 * Copyright 2008 Google Inc. All Rights Reserved.
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

package com.google.apphosting.utils.config;

import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.AutomaticScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.BasicScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.ClassLoaderConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.CpuUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;
import com.google.apphosting.utils.config.AppEngineWebXml.ManualScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.Pagespeed;
import com.google.apphosting.utils.config.AppEngineWebXml.PrioritySpecifierEntry;
import com.google.apphosting.utils.config.AppEngineWebXml.HealthCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.Resources;
import com.google.apphosting.utils.config.AppEngineWebXml.Network;

import org.eclipse.jetty.xml.XmlParser;
import org.eclipse.jetty.xml.XmlParser.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Constructs an {@link AppEngineWebXml} from an xml document corresponding to
 * appengine-web.xsd.  We use Jetty's {@link XmlParser} utility for
 * convenience.
 *
 * @TODO(user): Add a real link to the xsd once it exists and do schema
 * validation.
 *
 */
class AppEngineWebXmlProcessor {

  enum FileType { STATIC, RESOURCE }

  private static final Logger logger = Logger.getLogger(AppEngineWebXmlProcessor.class.getName());

  /**
   * Construct an {@link AppEngineWebXml} from the xml document
   * identified by the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process.
   *
   * @return Object representation of the xml document.
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  public AppEngineWebXml processXml(InputStream is) {
    XmlParser.Node config = getTopLevelNode(is);
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.setWarmupRequestsEnabled(true);
    for (Object o : config) {
      if (!(o instanceof XmlParser.Node)) {
        continue;
      }
      XmlParser.Node node = (XmlParser.Node) o;
      processSecondLevelNode(node, appEngineWebXml);
    }
    checkScalingConstraints(appEngineWebXml);
    return appEngineWebXml;
  }

  /**
   * Given an AppEngineWebXml, ensure it has no more than one of the scaling options available.
   *
   * @throws AppEngineConfigException If there is more than one scaling option selected.
   */
  private static void checkScalingConstraints(AppEngineWebXml appEngineWebXml) {
    int count = appEngineWebXml.getManualScaling().isEmpty() ? 0 : 1;
    count += appEngineWebXml.getBasicScaling().isEmpty() ? 0 : 1;
    count += appEngineWebXml.getAutomaticScaling().isEmpty() ? 0 : 1;
    if (count > 1) {
      throw new AppEngineConfigException(
          "There may be only one of 'automatic-scaling', 'manual-scaling' or " +
          "'basic-scaling' elements.");
    }
  }

  /**
   * Given an InputStream, create a Node corresponding to the top level xml
   * element.
   *
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  XmlParser.Node getTopLevelNode(InputStream is) {
    XmlParser xmlParser = new XmlParser();
    try {
      return xmlParser.parse(is);
    } catch (IOException e) {
      String msg = "Received IOException parsing the input stream.";
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    } catch (SAXException e) {
      String msg = "Received SAXException parsing the input stream.";
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    }
  }

  private void processSecondLevelNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    String elementName = node.getTag();
    if (elementName.equals("system-properties")) {
      processSystemPropertiesNode(node, appEngineWebXml);
    } else if (elementName.equals("vm-settings") || elementName.equals("beta-settings")) {
      processBetaSettingsNode(node, appEngineWebXml);
    } else if (elementName.equals("vm-health-check") || elementName.equals("health-check")) {
      processHealthCheckNode(node, appEngineWebXml);
    } else if (elementName.equals("resources")) {
      processResourcesNode(node, appEngineWebXml);
    } else if (elementName.equals("network")) {
      processNetworkNode(node, appEngineWebXml);
    } else if (elementName.equals("env-variables")) {
      processEnvironmentVariablesNode(node, appEngineWebXml);
    } else if (elementName.equals("application")) {
      processApplicationNode(node, appEngineWebXml);
    } else if (elementName.equals("version")) {
      processVersionNode(node, appEngineWebXml);
    } else if (elementName.equals("source-language")) {
      processSourceLanguageNode(node, appEngineWebXml);
    } else if (elementName.equals("module")) {
      processModuleNode(node, appEngineWebXml);
    } else if (elementName.equals("instance-class")) {
      processInstanceClassNode(node, appEngineWebXml);
    } else if (elementName.equals("automatic-scaling")) {
      processAutomaticScalingNode(node, appEngineWebXml);
    } else if (elementName.equals("manual-scaling")) {
      processManualScalingNode(node, appEngineWebXml);
    } else if (elementName.equals("basic-scaling")) {
      processBasicScalingNode(node, appEngineWebXml);
    } else if (elementName.equals("static-files")) {
      processFilesetNode(node, appEngineWebXml, FileType.STATIC);
    } else if (elementName.equals("resource-files")) {
      processFilesetNode(node, appEngineWebXml, FileType.RESOURCE);
    } else if (elementName.equals("ssl-enabled")) {
      processSslEnabledNode(node, appEngineWebXml);
    } else if (elementName.equals("sessions-enabled")) {
      processSessionsEnabledNode(node, appEngineWebXml);
    } else if (elementName.equals("async-session-persistence")) {
      processAsyncSessionPersistenceNode(node, appEngineWebXml);
    } else if (elementName.equals("user-permissions")) {
      processPermissionsNode(node, appEngineWebXml);
    } else if (elementName.equals("public-root")) {
      processPublicRootNode(node, appEngineWebXml);
    } else if (elementName.equals("inbound-services")) {
      processInboundServicesNode(node, appEngineWebXml);
    } else if (elementName.equals("precompilation-enabled")) {
      processPrecompilationEnabledNode(node, appEngineWebXml);
    } else if (elementName.equals("admin-console")) {
      processAdminConsoleNode(node, appEngineWebXml);
    } else if (elementName.equals("static-error-handlers")) {
      processErrorHandlerNode(node, appEngineWebXml);
    } else if (elementName.equals("warmup-requests-enabled")) {
      processWarmupRequestsEnabledNode(node, appEngineWebXml);
    } else if (elementName.equals("threadsafe")) {
      processThreadsafeNode(node, appEngineWebXml);
    } else if (elementName.equals("auto-id-policy")) {
      processAutoIdPolicyNode(node, appEngineWebXml);
    } else if (elementName.equals("code-lock")) {
      processCodeLockNode(node, appEngineWebXml);
    } else if (elementName.equals("vm")) {
      processVmNode(node, appEngineWebXml);
    } else if (elementName.equals("api-config")) {
      processApiConfigNode(node, appEngineWebXml);
    } else if (elementName.equals("pagespeed")) {
      processPagespeedNode(node, appEngineWebXml);
    } else if (elementName.equals("class-loader-config")) {
      processClassLoaderConfig(node, appEngineWebXml);
    } else if (elementName.equals("url-stream-handler")) {
      processUrlStreamHandler(node, appEngineWebXml);
    } else if (elementName.equals("use-google-connector-j")) {
      processUseGoogleConnectorJNode(node, appEngineWebXml);
    } else {
      throw new AppEngineConfigException("Unrecognized element <" + elementName + ">");
    }
  }

  private void processApplicationNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAppId(getTextNode(node));
  }

  private void processPublicRootNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPublicRoot(getTextNode(node));
  }

  private void processVersionNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setMajorVersionId(getTextNode(node));
  }

  private void processSourceLanguageNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSourceLanguage(getTextNode(node));
  }

  private void processModuleNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setModule(getTextNode(node));
  }

  private void processInstanceClassNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setInstanceClass(getTextNode(node));
  }

  private String getChildNodeText(XmlParser.Node parentNode, String childTag) {
    String result = null;
    XmlParser.Node node = parentNode.get(childTag);
    if (node != null) {
      result = (String) node.get(0);
    }
    return result;
  }

  private Integer getChildNodePositiveInteger(XmlParser.Node parentNode, String childTag) {
    Integer result = null;
    XmlParser.Node node = parentNode.get(childTag);
    if (node != null && node.get(0) != null) {
      String trimmedText = ((String) node.get(0)).trim();
      if (!trimmedText.isEmpty()) {
        try {
          result = Integer.parseInt(trimmedText);
        } catch (NumberFormatException ex) {
          throw new AppEngineConfigException(childTag + " should only contain integers.");
        }

        if (result <= 0) {
          throw new AppEngineConfigException(childTag + " should only contain positive integers.");
        }
      }
    }
    return result;
  }

  private Double getChildNodeDouble(XmlParser.Node parentNode, String childTag) {
    Double result = null;
    XmlParser.Node node = parentNode.get(childTag);
    if (node != null && node.get(0) != null) {
      String trimmedText = ((String) node.get(0)).trim();
      if (!trimmedText.isEmpty()) {
        try {
          result = Double.parseDouble(trimmedText);
        } catch (NumberFormatException ex) {
          throw new AppEngineConfigException(childTag + " should only contain doubles.");
        } catch (NullPointerException ex) {
          throw new AppEngineConfigException(childTag + " should NOT be empty.");
        }
      }
    }
    return result;
  }

  private void processAutomaticScalingNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinPendingLatency(getChildNodeText(settingsNode, "min-pending-latency"));
    automaticScaling.setMaxPendingLatency(getChildNodeText(settingsNode, "max-pending-latency"));
    automaticScaling.setMinIdleInstances(getChildNodeText(settingsNode, "min-idle-instances"));
    automaticScaling.setMaxIdleInstances(getChildNodeText(settingsNode, "max-idle-instances"));
    automaticScaling.setMaxConcurrentRequests(
        getChildNodeText(settingsNode, "max-concurrent-requests"));
    automaticScaling.setMinNumInstances(
        getChildNodePositiveInteger(settingsNode, "min-num-instances"));
    automaticScaling.setMaxNumInstances(
        getChildNodePositiveInteger(settingsNode, "max-num-instances"));
    automaticScaling.setCoolDownPeriodSec(
        getChildNodePositiveInteger(settingsNode, "cool-down-period-sec"));
    processCpuUtilizationNode(settingsNode, automaticScaling);
  }

  private void processCpuUtilizationNode(
      XmlParser.Node settingsNode, AutomaticScaling automaticScaling) {
    XmlParser.Node childNode = settingsNode.get("cpu-utilization");
    if (childNode != null) {
      CpuUtilization cpuUtilization = new CpuUtilization();
      Double targetUtilization = getChildNodeDouble(childNode, "target-utilization");
      if (targetUtilization != null) {
        if (targetUtilization <= 0 || targetUtilization > 1) {
          throw new AppEngineConfigException("target-utilization should be in range (0, 1].");
        }
        cpuUtilization.setTargetUtilization(targetUtilization);
      }

      cpuUtilization.setAggregationWindowLengthSec(
          getChildNodePositiveInteger(childNode, "aggregation-window-length-sec"));
      if (!cpuUtilization.isEmpty()) {
        automaticScaling.setCpuUtilization(cpuUtilization);
      }
    }
  }

  private void processManualScalingNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    ManualScaling manualScaling = appEngineWebXml.getManualScaling();
    manualScaling.setInstances(getChildNodeText(settingsNode, "instances"));
  }

  private void processBasicScalingNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    BasicScaling basicScaling = appEngineWebXml.getBasicScaling();
    basicScaling.setMaxInstances(getChildNodeText(settingsNode, "max-instances"));
    basicScaling.setIdleTimeout(getChildNodeText(settingsNode, "idle-timeout"));
  }

  private void processSslEnabledNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSslEnabled(getBooleanValue(node));
  }

  private void processSessionsEnabledNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSessionsEnabled(getBooleanValue(node));
  }

  private void processAsyncSessionPersistenceNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    boolean enabled = getBooleanAttributeValue(node, "enabled");
    appEngineWebXml.setAsyncSessionPersistence(enabled);
    String queueName = trim(node.getAttribute("queue-name"));
    appEngineWebXml.setAsyncSessionPersistenceQueueName(queueName);
  }

  private void processPrecompilationEnabledNode(XmlParser.Node node,
                                                AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPrecompilationEnabled(getBooleanValue(node));
  }

  private void processWarmupRequestsEnabledNode(XmlParser.Node node,
                                                AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setWarmupRequestsEnabled(getBooleanValue(node));
  }

  private void processThreadsafeNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setThreadsafe(getBooleanValue(node));
  }

  private void processAutoIdPolicyNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAutoIdPolicy(getTextNode(node));
  }

  private void processCodeLockNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setCodeLock(getBooleanValue(node));
  }

  private void processVmNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseVm(getBooleanValue(node));
  }

  private void processFilesetNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml,
      FileType type) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "include");
    while (nodeIter.hasNext()) {
      XmlParser.Node includeNode = nodeIter.next();
      String path = trim(includeNode.getAttribute("path"));
      if (type == FileType.STATIC) {
        String expiration = trim(includeNode.getAttribute("expiration"));
        AppEngineWebXml.StaticFileInclude staticFileInclude =
            appEngineWebXml.includeStaticPattern(path, expiration);

        Map<String, String> httpHeaders = staticFileInclude.getHttpHeaders();
        Iterator<XmlParser.Node> httpHeaderIter = getNodeIterator(includeNode, "http-header");
        while (httpHeaderIter.hasNext()) {
          XmlParser.Node httpHeaderNode = httpHeaderIter.next();
          String name = httpHeaderNode.getAttribute("name");
          String value = httpHeaderNode.getAttribute("value");

          if (httpHeaders.containsKey(name)) {
            throw new AppEngineConfigException("Two http-header elements have the same name.");
          }

          httpHeaders.put(name, value);
        }
      } else {
        appEngineWebXml.includeResourcePattern(path);
      }
    }

    nodeIter = getNodeIterator(node, "exclude");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String path = trim(subNode.getAttribute("path"));
      if (type == FileType.STATIC) {
        appEngineWebXml.excludeStaticPattern(path);
      } else {
        appEngineWebXml.excludeResourcePattern(path);
      }
    }
  }

  private Iterator<XmlParser.Node> getNodeIterator(XmlParser.Node node, String filter) {
    @SuppressWarnings("unchecked")
    Iterator<XmlParser.Node> iterator = node.iterator(filter);
    return iterator;
  }

  private void processSystemPropertiesNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "property");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      appEngineWebXml.addSystemProperty(propertyName, propertyValue);
    }
  }

  private void processBetaSettingsNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "setting");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String name = trim(subNode.getAttribute("name"));
      String value = trim(subNode.getAttribute("value"));
      appEngineWebXml.addBetaSetting(name, value);
    }
  }

  private void processHealthCheckNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    HealthCheck healthCheck = appEngineWebXml.getHealthCheck();
    String enableHealthCheck = trim(getChildNodeText(settingsNode, "enable-health-check"));
    if (enableHealthCheck != null && !enableHealthCheck.isEmpty()) {
      healthCheck.setEnableHealthCheck(toBoolean(enableHealthCheck));
    }
    healthCheck.setCheckIntervalSec(
        getChildNodePositiveInteger(settingsNode, "check-interval-sec"));
    healthCheck.setTimeoutSec(getChildNodePositiveInteger(settingsNode, "timeout-sec"));
    healthCheck.setUnhealthyThreshold(
        getChildNodePositiveInteger(settingsNode, "unhealthy-threshold"));
    healthCheck.setHealthyThreshold(
        getChildNodePositiveInteger(settingsNode, "healthy-threshold"));
    healthCheck.setRestartThreshold(
        getChildNodePositiveInteger(settingsNode, "restart-threshold"));
    healthCheck.setHost(getChildNodeText(settingsNode, "host"));
  }

  private void processResourcesNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    Resources resources = appEngineWebXml.getResources();
    Double cpu = getChildNodeDouble(settingsNode, "cpu");
    if (cpu != null) {
      resources.setCpu(cpu);
    }
    Double memory_gb = getChildNodeDouble(settingsNode, "memory-gb");
    if (memory_gb != null) {
      resources.setMemoryGb(memory_gb);
    }
    Integer disk_size_gb = getChildNodePositiveInteger(settingsNode, "disk-size-gb");
    if (disk_size_gb != null) {
      resources.setDiskSizeGb(disk_size_gb);
    }
  }

  private void processNetworkNode(XmlParser.Node settingsNode,
      AppEngineWebXml appEngineWebXml) {
    Network network = appEngineWebXml.getNetwork();
    String instance_tag = trim(getChildNodeText(settingsNode, "instance-tag"));
    if (instance_tag != null && !instance_tag.isEmpty()) {
      network.setInstanceTag(instance_tag);
    }
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(settingsNode, "forwarded-port");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String forwardedPort = getTextNode(subNode);
      network.addForwardedPort(forwardedPort);
    }
  }

  private void processEnvironmentVariablesNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "env-var");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      appEngineWebXml.addEnvironmentVariable(propertyName, propertyValue);
    }
  }

  private void processPermissionsNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "permission");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String className = trim(subNode.getAttribute("class"));
      String name = trim(subNode.getAttribute("name"));
      String actions = trim(subNode.getAttribute("actions"));
      appEngineWebXml.addUserPermission(className, name, actions);
    }
  }

  private void processInboundServicesNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "service");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String service = getTextNode(subNode);
      appEngineWebXml.addInboundService(service);
    }
  }

  private void processAdminConsoleNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "page");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String name = trim(subNode.getAttribute("name"));
      String url = trim(subNode.getAttribute("url"));
      appEngineWebXml.addAdminConsolePage(new AdminConsolePage(name, url));
    }
  }

  private void processErrorHandlerNode(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "handler");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String file = trim(subNode.getAttribute("file"));
      String errorCode = trim(subNode.getAttribute("error-code"));
      appEngineWebXml.addErrorHandler(new ErrorHandler(file, errorCode));
    }
  }

  private void processApiConfigNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    String servlet = trim(node.getAttribute("servlet-class"));
    String url = trim(node.getAttribute("url-pattern"));
    appEngineWebXml.setApiConfig(new ApiConfig(servlet, url));

    String id = null;
    Iterator<XmlParser.Node> subNodeIter = getNodeIterator(node, "endpoint-servlet-mapping-id");
    while (subNodeIter.hasNext()) {
      XmlParser.Node subNode = subNodeIter.next();
      id = trim(getTextNode(subNode));
      if (id != null && id.length() > 0) {
        appEngineWebXml.addApiEndpoint(id);
      }
    }
  }

  private void processPagespeedNode(XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    Pagespeed pagespeed = new Pagespeed();
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "url-blacklist");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String urlMatcher = getTextNode(subNode);
      pagespeed.addUrlBlacklist(urlMatcher);
    }
    nodeIter = getNodeIterator(node, "domain-to-rewrite");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String domain = getTextNode(subNode);
      pagespeed.addDomainToRewrite(domain);
    }
    nodeIter = getNodeIterator(node, "enabled-rewriter");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String rewriter = getTextNode(subNode);
      pagespeed.addEnabledRewriter(rewriter);
    }
    nodeIter = getNodeIterator(node, "disabled-rewriter");
    while (nodeIter.hasNext()) {
      XmlParser.Node subNode = nodeIter.next();
      String rewriter = getTextNode(subNode);
      pagespeed.addDisabledRewriter(rewriter);
    }
    appEngineWebXml.setPagespeed(pagespeed);
  }

  private void processClassLoaderConfig(
      XmlParser.Node node, AppEngineWebXml appEngineWebXml) {
    ClassLoaderConfig config = new ClassLoaderConfig();
    appEngineWebXml.setClassLoaderConfig(config);
    Iterator<XmlParser.Node> nodeIter = getNodeIterator(node, "priority-specifier");
    while (nodeIter.hasNext()) {
      processClassPathPrioritySpecifier(nodeIter.next(), config);
    }
  }

  private void processClassPathPrioritySpecifier(Node node, ClassLoaderConfig config) {
    PrioritySpecifierEntry entry = new PrioritySpecifierEntry();
    entry.setFilename(node.getAttribute("filename"));
    entry.setPriority(node.getAttribute("priority"));
    entry.checkClassLoaderConfig();
    config.add(entry);
  }

  private void processUrlStreamHandler(Node node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUrlStreamHandlerType(getTextNode(node));
  }

  private boolean getBooleanValue(XmlParser.Node node) {
    return toBoolean(getTextNode(node));
  }

  private boolean getBooleanAttributeValue(XmlParser.Node node, String attribute) {
    return toBoolean(node.getAttribute(attribute));
  }

  private boolean toBoolean(String value) {
    value = value.trim();
    return (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private String getTextNode(XmlParser.Node node) {
    String value = (String) node.get(0);
    if (value == null) {
      value = "";
    }
    return value.trim();
  }

  private String trim(String attribute) {
    return attribute == null ? null : attribute.trim();
  }

  private void processUseGoogleConnectorJNode(XmlParser.Node node,
                                              AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseGoogleConnectorJ(getBooleanValue(node));
  }
}

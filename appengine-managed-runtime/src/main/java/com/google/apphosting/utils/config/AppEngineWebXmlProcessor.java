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
import com.google.apphosting.utils.config.AppEngineWebXml.HealthCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.ManualScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.Network;
import com.google.apphosting.utils.config.AppEngineWebXml.Pagespeed;
import com.google.apphosting.utils.config.AppEngineWebXml.PrioritySpecifierEntry;
import com.google.apphosting.utils.config.AppEngineWebXml.Resources;
import com.google.apphosting.vmruntime.VmRuntimeLogHandler;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.google.apphosting.vmruntime.VmRuntimeLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Constructs an {@link AppEngineWebXml} from an xml document corresponding to
 * appengine-web.xsd.
 *
 * TODO(user): Add a real link to the xsd once it exists and do schema
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
    Element config = getTopLevelNode(is);
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    // Warmup requests are on by default for Java applications
    // configured via an appengine-web.xml.
    appEngineWebXml.setWarmupRequestsEnabled(true);
    NodeList nodes = config.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      processSecondLevelNode(Element.class.cast(node), appEngineWebXml);
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
  Element getTopLevelNode(InputStream is) {
    return XmlUtils.parseXml(is).getDocumentElement();
  }

  @SuppressWarnings("IfCanBeSwitch")
  private void processSecondLevelNode(Element elt, AppEngineWebXml appEngineWebXml) {
    String elementName = elt.getTagName();
    if (elementName.equals("system-properties")) {
      processSystemPropertiesNode(elt, appEngineWebXml);
    } else if (elementName.equals("vm-settings") || elementName.equals("beta-settings")) {
      processBetaSettingsNode(elt, appEngineWebXml);
    } else if (elementName.equals("vm-health-check") || elementName.equals("health-check")) {
      processHealthCheckNode(elt, appEngineWebXml);
    } else if (elementName.equals("resources")) {
      processResourcesNode(elt, appEngineWebXml);
    } else if (elementName.equals("network")) {
      processNetworkNode(elt, appEngineWebXml);
    } else if (elementName.equals("env-variables")) {
      processEnvironmentVariablesNode(elt, appEngineWebXml);
    } else if (elementName.equals("application")) {
      processApplicationNode(elt, appEngineWebXml);
    } else if (elementName.equals("version")) {
      processVersionNode(elt, appEngineWebXml);
    } else if (elementName.equals("source-language")) {
      processSourceLanguageNode(elt, appEngineWebXml);
    } else if (elementName.equals("module")) {
      processModuleNode(elt, appEngineWebXml);
    } else if (elementName.equals("instance-class")) {
      processInstanceClassNode(elt, appEngineWebXml);
    } else if (elementName.equals("automatic-scaling")) {
      processAutomaticScalingNode(elt, appEngineWebXml);
    } else if (elementName.equals("manual-scaling")) {
      processManualScalingNode(elt, appEngineWebXml);
    } else if (elementName.equals("basic-scaling")) {
      processBasicScalingNode(elt, appEngineWebXml);
    } else if (elementName.equals("static-files")) {
      processFilesetNode(elt, appEngineWebXml, FileType.STATIC);
    } else if (elementName.equals("resource-files")) {
      processFilesetNode(elt, appEngineWebXml, FileType.RESOURCE);
    } else if (elementName.equals("ssl-enabled")) {
      processSslEnabledNode(elt, appEngineWebXml);
    } else if (elementName.equals("sessions-enabled")) {
      processSessionsEnabledNode(elt, appEngineWebXml);
    } else if (elementName.equals("async-session-persistence")) {
      processAsyncSessionPersistenceNode(elt, appEngineWebXml);
    } else if (elementName.equals("user-permissions")) {
      processPermissionsNode(elt, appEngineWebXml);
    } else if (elementName.equals("public-root")) {
      processPublicRootNode(elt, appEngineWebXml);
    } else if (elementName.equals("inbound-services")) {
      processInboundServicesNode(elt, appEngineWebXml);
    } else if (elementName.equals("precompilation-enabled")) {
      processPrecompilationEnabledNode(elt, appEngineWebXml);
    } else if (elementName.equals("admin-console")) {
      processAdminConsoleNode(elt, appEngineWebXml);
    } else if (elementName.equals("static-error-handlers")) {
      processErrorHandlerNode(elt, appEngineWebXml);
    } else if (elementName.equals("warmup-requests-enabled")) {
      processWarmupRequestsEnabledNode(elt, appEngineWebXml);
    } else if (elementName.equals("threadsafe")) {
      processThreadsafeNode(elt, appEngineWebXml);
    } else if (elementName.equals("auto-id-policy")) {
      processAutoIdPolicyNode(elt, appEngineWebXml);
    } else if (elementName.equals("code-lock")) {
      processCodeLockNode(elt, appEngineWebXml);
    } else if (elementName.equals("vm")) {
      processVmNode(elt, appEngineWebXml);
    } else if (elementName.equals("env")) {
      processEnvNode(elt, appEngineWebXml);
    } else if (elementName.equals("api-config")) {
      processApiConfigNode(elt, appEngineWebXml);
    } else if (elementName.equals("pagespeed")) {
      processPagespeedNode(elt, appEngineWebXml);
    } else if (elementName.equals("class-loader-config")) {
      processClassLoaderConfig(elt, appEngineWebXml);
    } else if (elementName.equals("url-stream-handler")) {
      processUrlStreamHandler(elt, appEngineWebXml);
    } else if (elementName.equals("use-google-connector-j")) {
      processUseGoogleConnectorJNode(elt, appEngineWebXml);
    } else {
      throw new AppEngineConfigException("Unrecognized element <" + elementName + ">");
    }
  }

  private void processApplicationNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAppId(getTextNode(node));
  }

  private void processPublicRootNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPublicRoot(getTextNode(node));
  }

  private void processVersionNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setMajorVersionId(getTextNode(node));
  }

  private void processSourceLanguageNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSourceLanguage(getTextNode(node));
  }

  private void processModuleNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setModule(getTextNode(node));
  }

  private void processInstanceClassNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setInstanceClass(getTextNode(node));
  }

  private String getChildNodeText(Element parentNode, String childTag) {
    String result = null;
    Element node = XmlUtils.getChildElement(parentNode, childTag);
    if (node != null) {
      result = XmlUtils.getBody(node);
    }
    return result;
  }

  private Integer getChildNodePositiveInteger(Element parentNode, String childTag) {
    Integer result = null;
    Element node = XmlUtils.getChildElement(parentNode, childTag);
    if (node != null && XmlUtils.getBody(node) != null) {
      String trimmedText = (XmlUtils.getBody(node)).trim();
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

  private Double getChildNodeDouble(Element parentNode, String childTag) {
    Double result = null;
    Element node = XmlUtils.getChildElement(parentNode, childTag);
    if (node != null && XmlUtils.getBody(node) != null) {
      String trimmedText = (XmlUtils.getBody(node)).trim();
      if (!trimmedText.isEmpty()) {
        try {
          result = Double.parseDouble(trimmedText);
        } catch (NumberFormatException ex) {
          throw new AppEngineConfigException(childTag + " should only contain doubles.");
        } catch (NullPointerException ex) {
          // This shouldn't happen because of the check above. However, Keep it here for
          // completeness of catching exceptions.
          throw new AppEngineConfigException(childTag + " should NOT be empty.");
        }
      }
    }
    return result;
  }
  private void processAutomaticScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
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
    automaticScaling.setTargetNetworkSentBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-sent-bytes-per-sec"));
    automaticScaling.setTargetNetworkSentPacketsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-sent-packets-per-sec"));
    automaticScaling.setTargetNetworkReceivedBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-received-bytes-per-sec"));
    automaticScaling.setTargetNetworkReceivedPacketsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-received-packets-per-sec"));
    automaticScaling.setTargetDiskWriteBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-write-bytes-per-sec"));
    automaticScaling.setTargetDiskWriteOpsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-write-ops-per-sec"));
    automaticScaling.setTargetDiskReadBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-read-bytes-per-sec"));
    automaticScaling.setTargetDiskReadOpsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-read-ops-per-sec"));
    automaticScaling.setTargetRequestCountPerSec(
        getChildNodePositiveInteger(settingsNode, "target-request-count-per-sec"));
    automaticScaling.setTargetConcurrentRequests(
        getChildNodePositiveInteger(settingsNode, "target-concurrent-requests"));
  }

  private void processCpuUtilizationNode(Element settingsNode, AutomaticScaling automaticScaling) {
    Element childNode = XmlUtils.getChildElement(settingsNode, "cpu-utilization");
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

  private void processManualScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    ManualScaling manualScaling = appEngineWebXml.getManualScaling();
    manualScaling.setInstances(getChildNodeText(settingsNode, "instances"));
  }

  private void processBasicScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    BasicScaling basicScaling = appEngineWebXml.getBasicScaling();
    basicScaling.setMaxInstances(getChildNodeText(settingsNode, "max-instances"));
    basicScaling.setIdleTimeout(getChildNodeText(settingsNode, "idle-timeout"));
  }

  private void processSslEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSslEnabled(getBooleanValue(node));
  }

  private void processSessionsEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSessionsEnabled(getBooleanValue(node));
  }

  private void processAsyncSessionPersistenceNode(Element node, AppEngineWebXml appEngineWebXml) {
    boolean enabled = getBooleanAttributeValue(node, "enabled");
    appEngineWebXml.setAsyncSessionPersistence(enabled);
    String queueName = trim(node.getAttribute("queue-name"));
    if (queueName.equals("")) {
      queueName = null;
    }
    appEngineWebXml.setAsyncSessionPersistenceQueueName(queueName);
  }

  private void processPrecompilationEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPrecompilationEnabled(getBooleanValue(node));
  }

  private void processWarmupRequestsEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setWarmupRequestsEnabled(getBooleanValue(node));
  }

  private void processThreadsafeNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setThreadsafe(getBooleanValue(node));
  }

  private void processAutoIdPolicyNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAutoIdPolicy(getTextNode(node));
  }

  private void processCodeLockNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setCodeLock(getBooleanValue(node));
  }

  private void processVmNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseVm(getBooleanValue(node));
  }

  private void processEnvNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setEnv(getTextNode(node));
  }

  private void processFilesetNode(Element node, AppEngineWebXml appEngineWebXml, FileType type) {
    Iterator<Element> nodeIter = getNodeIterator(node, "include");
    while (nodeIter.hasNext()) {
      Element includeNode = nodeIter.next();
      String path = trim(includeNode.getAttribute("path"));
      if (path.equals("")) {
        path = null;
      }
      if (type == FileType.STATIC) {
        String expiration = trim(includeNode.getAttribute("expiration"));
        if (expiration.equals("")) {
          expiration = null;
        }
        AppEngineWebXml.StaticFileInclude staticFileInclude =
            appEngineWebXml.includeStaticPattern(path, expiration);

        Map<String, String> httpHeaders = staticFileInclude.getHttpHeaders();
        Iterator<Element> httpHeaderIter = getNodeIterator(includeNode, "http-header");
        while (httpHeaderIter.hasNext()) {
          Element httpHeaderNode = httpHeaderIter.next();
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
      Element subNode = nodeIter.next();
      String path = trim(subNode.getAttribute("path"));
      if (type == FileType.STATIC) {
        appEngineWebXml.excludeStaticPattern(path);
      } else {
        appEngineWebXml.excludeResourcePattern(path);
      }
    }
  }

  private Iterator<Element> getNodeIterator(Element node, String filter) {
    return XmlUtils.getChildren(node, filter).iterator();
  }

  private void processSystemPropertiesNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "property");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      
      if (VmRuntimeLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY.equals(propertyName) && System.getProperty(VmRuntimeLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY)!=null)
        propertyValue = System.getProperty(VmRuntimeLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY);
      
      appEngineWebXml.addSystemProperty(propertyName, propertyValue);
    }
  }

  private void processBetaSettingsNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "setting");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String name = trim(subNode.getAttribute("name"));
      String value = trim(subNode.getAttribute("value"));
      appEngineWebXml.addBetaSetting(name, value);
    }
  }

  private void processHealthCheckNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
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

  private void processResourcesNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
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

  private void processNetworkNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    Network network = appEngineWebXml.getNetwork();
    String instance_tag = trim(getChildNodeText(settingsNode, "instance-tag"));
    if (instance_tag != null && !instance_tag.isEmpty()) {
      network.setInstanceTag(instance_tag);
    }
    Iterator<Element> nodeIter = getNodeIterator(settingsNode, "forwarded-port");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String forwardedPort = getTextNode(subNode);
      network.addForwardedPort(forwardedPort);
    }
    String name = trim(getChildNodeText(settingsNode, "name"));
    if (name != null && !name.isEmpty()) {
      network.setName(name);
    }
  }

  private void processEnvironmentVariablesNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "env-var");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      appEngineWebXml.addEnvironmentVariable(propertyName, propertyValue);
    }
  }

  private void processPermissionsNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "permission");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String className = trim(subNode.getAttribute("class"));
      if (className.equals("")) {
        className = null;
      }
      String name = trim(subNode.getAttribute("name"));
      if (name.equals("")) {
        name = null;
      }
      String actions = trim(subNode.getAttribute("actions"));
      if (actions.equals("")) {
        actions = null;
      }
      appEngineWebXml.addUserPermission(className, name, actions);
    }
  }

  private void processInboundServicesNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "service");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String service = getTextNode(subNode);
      appEngineWebXml.addInboundService(service);
    }
  }

  private void processAdminConsoleNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "page");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String name = trim(subNode.getAttribute("name"));
      String url = trim(subNode.getAttribute("url"));
      appEngineWebXml.addAdminConsolePage(new AdminConsolePage(name, url));
    }
  }

  private void processErrorHandlerNode(Element node, AppEngineWebXml appEngineWebXml) {
    Iterator<Element> nodeIter = getNodeIterator(node, "handler");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String file = trim(subNode.getAttribute("file"));
      if (file.equals("")) {
        file = null;
      }
      String errorCode = trim(subNode.getAttribute("error-code"));
      if (errorCode.equals("")) {
        errorCode = null;
      }
      appEngineWebXml.addErrorHandler(new ErrorHandler(file, errorCode));
    }
  }

  private void processApiConfigNode(Element node, AppEngineWebXml appEngineWebXml) {
    String servlet = trim(node.getAttribute("servlet-class"));
    String url = trim(node.getAttribute("url-pattern"));
    appEngineWebXml.setApiConfig(new ApiConfig(servlet, url));

    String id;
    Iterator<Element> subNodeIter = getNodeIterator(node, "endpoint-servlet-mapping-id");
    while (subNodeIter.hasNext()) {
      Element subNode = subNodeIter.next();
      id = trim(getTextNode(subNode));
      if (id != null && id.length() > 0) {
        appEngineWebXml.addApiEndpoint(id);
      }
    }
  }

  private void processPagespeedNode(Element node, AppEngineWebXml appEngineWebXml) {
    Pagespeed pagespeed = new Pagespeed();
    Iterator<Element> nodeIter = getNodeIterator(node, "url-blacklist");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String urlMatcher = getTextNode(subNode);
      pagespeed.addUrlBlacklist(urlMatcher);
    }
    nodeIter = getNodeIterator(node, "domain-to-rewrite");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String domain = getTextNode(subNode);
      pagespeed.addDomainToRewrite(domain);
    }
    nodeIter = getNodeIterator(node, "enabled-rewriter");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String rewriter = getTextNode(subNode);
      pagespeed.addEnabledRewriter(rewriter);
    }
    nodeIter = getNodeIterator(node, "disabled-rewriter");
    while (nodeIter.hasNext()) {
      Element subNode = nodeIter.next();
      String rewriter = getTextNode(subNode);
      pagespeed.addDisabledRewriter(rewriter);
    }
    appEngineWebXml.setPagespeed(pagespeed);
  }

  private void processClassLoaderConfig(Element node, AppEngineWebXml appEngineWebXml) {
    ClassLoaderConfig config = new ClassLoaderConfig();
    appEngineWebXml.setClassLoaderConfig(config);
    Iterator<Element> nodeIter = getNodeIterator(node, "priority-specifier");
    while (nodeIter.hasNext()) {
      processClassPathPrioritySpecifier(nodeIter.next(), config);
    }
  }

  private void processClassPathPrioritySpecifier(Element node, ClassLoaderConfig config) {
    PrioritySpecifierEntry entry = new PrioritySpecifierEntry();
    entry.setFilename(XmlUtils.getAttribute(node, "filename"));
    entry.setPriority(XmlUtils.getAttribute(node, "priority"));
    entry.checkClassLoaderConfig();
    config.add(entry);
  }

  private void processUrlStreamHandler(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUrlStreamHandlerType(getTextNode(node));
  }

  private boolean getBooleanValue(Element node) {
    return toBoolean(getTextNode(node));
  }

  private boolean getBooleanAttributeValue(Element node, String attribute) {
    return toBoolean(node.getAttribute(attribute));
  }

  private boolean toBoolean(String value) {
    value = value.trim();
    return (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private String getTextNode(Element node) {
    String value = XmlUtils.getBody(node);
    if (value == null) {
      value = "";
    }
    return value.trim();
  }

  private String trim(String attribute) {
    return attribute == null ? null : attribute.trim();
  }

  private void processUseGoogleConnectorJNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseGoogleConnectorJ(getBooleanValue(node));
  }
}



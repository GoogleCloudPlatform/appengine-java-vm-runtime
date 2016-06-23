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

import static org.junit.Assert.assertNotEquals;

import com.google.apphosting.utils.config.AppEngineWebXml.ScalingType;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class AppEngineWebXmlTest extends TestCase {

  private static final String WHITE_SPACE = "\t \n";
  private static final String EMPTY = "";
  private static final String AUTOMATIC = "automatic";

  public void testApiEndpointExplicit() {
    AppEngineWebXml xml = new AppEngineWebXml();
    xml.addApiEndpoint("foo");
    assertTrue(xml.isApiEndpoint("foo"));
    assertFalse(xml.isApiEndpoint("bar"));
  }

  public void testThreadsafeTracking() {
    AppEngineWebXml xml = new AppEngineWebXml();
    assertFalse(xml.getThreadsafeValueProvided());
    xml.setThreadsafe(false);
    assertTrue(xml.getThreadsafeValueProvided());

    xml = new AppEngineWebXml();
    xml.setThreadsafe(true);
    assertTrue(xml.getThreadsafeValueProvided());
  }

  public void testStaticFileIncludeEqualsNoExpires() {
    AppEngineWebXml.StaticFileInclude include1 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", null);

    // Things should equal themselves at least.
    assertEquals(include1, include1);
    assertEquals(include1.hashCode(), include1.hashCode());
    assertFalse(include1.equals(null));
    assertFalse(include1.equals("should not be equal"));

    // Things could be equal to other things.
    AppEngineWebXml.StaticFileInclude include2 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", null);
    assertEquals(include1, include2);
    assertEquals(include2, include1);
    assertEquals(include1.hashCode(), include2.hashCode());

    // A different path should make two StaticFileInclude instances NOT equal.
    AppEngineWebXml.StaticFileInclude include3 = new AppEngineWebXml.StaticFileInclude(
        "/moar-static-files/*", null);
    assertFalse(include1.equals(include3));
    assertFalse(include3.equals(include1));
    // Hash codes being equal is just "almost certainly" wrong (as opposed to definitely wrong).
    assertTrue(include1.hashCode() != include3.hashCode());

    // null pattern
    AppEngineWebXml.StaticFileInclude include4 = new AppEngineWebXml.StaticFileInclude(null, null);
    assertFalse(include1.equals(include4));
    assertFalse(include4.equals(include1));
    // Hash codes being equal is just "almost certainly" wrong (as opposed to definitely wrong).
    assertTrue(include1.hashCode() != include4.hashCode());

    // Different HTTP headers should make two StaticFileIncludes not equal.
    include2.getHttpHeaders().put("Name", "value");
    assertFalse(include1.equals(include2));
    assertFalse(include2.equals(include1));
    assertTrue(include1.hashCode() != include2.hashCode());

    // StaticFileIncludes can have HTTP headers and still be equal.
    include1.getHttpHeaders().put("Name", "value");
    assertEquals(include1, include2);
    assertEquals(include2, include1);
    assertEquals(include1.hashCode(), include2.hashCode());
  }

  public void testStaticFileIncludeEqualsWithExpires() {
    String expiration = "7d";
    AppEngineWebXml.StaticFileInclude include1 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", expiration);
    AppEngineWebXml.StaticFileInclude include2 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", expiration);

    // Things should equal themselves at least.
    assertEquals(include1, include1);
    assertEquals(include1.hashCode(), include1.hashCode());

    // Things could be equal to other things.
    assertEquals(include1, include2);
    assertEquals(include2, include1);
    assertEquals(include1.hashCode(), include2.hashCode());

    // A different expiration should make two StaticFileInclude instances NOT equal.
    AppEngineWebXml.StaticFileInclude include3 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", "10s");
    assertFalse(include1.equals(include3));
    assertFalse(include3.equals(include1));
    assertTrue(include1.hashCode() != include3.hashCode());

    // A different expiration should make two StaticFileInclude instances NOT equal.
    AppEngineWebXml.StaticFileInclude include4 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", null);
    assertFalse(include1.equals(include4));
    assertFalse(include4.equals(include1));
    assertTrue(include1.hashCode() != include4.hashCode());
  }

  public void testStaticFileIncludeHttpHeaderOrderPreserved() {
    AppEngineWebXml.StaticFileInclude include1 = new AppEngineWebXml.StaticFileInclude(
        "/static-files/*", null);
    Map<String, String> httpHeaders = include1.getHttpHeaders();
    httpHeaders.put("foo", "1");
    httpHeaders.put("bar", "2");
    Map<String, String> expected = new HashMap<>();
    expected.put("foo", "1");
    expected.put("bar", "2");
    assertEquals(expected, httpHeaders);
  }

  public void testPrioritySpecifierEntryEquals() {
    AppEngineWebXml.PrioritySpecifierEntry entry1 = new AppEngineWebXml.PrioritySpecifierEntry();
    AppEngineWebXml.PrioritySpecifierEntry entry2 = new AppEngineWebXml.PrioritySpecifierEntry();
    assertEquals(entry1, entry2);
    assertNotNull(entry1);
    entry1.setFilename("mailapi.jar");
    assertNotEquals(entry1, entry2);
    entry2.setFilename("mailapi.jar");
    assertEquals(entry1, entry2);
    entry1.setPriority("1.0");
    assertNotEquals(entry1, entry2);
    entry2.setPriority("1.0");
    assertEquals(entry1, entry2);
  }


  public void testPrioritySpecifierEntry() {
    AppEngineWebXml.PrioritySpecifierEntry entry = new AppEngineWebXml.PrioritySpecifierEntry();
    assertEquals(1.0d, entry.getPriorityValue());
    assertNull(entry.getPriority());
    entry.setFilename("mailapi.jar");
    try {
      entry.setFilename("another.jar");
      fail();
    } catch (AppEngineConfigException e) {
      // OK
    }
  }

  public void testClassLoaderConfigEquals() {
    AppEngineWebXml.PrioritySpecifierEntry ps = new AppEngineWebXml.PrioritySpecifierEntry();
    ps.setFilename("mailapi.jar");

    AppEngineWebXml.ClassLoaderConfig config1 = new AppEngineWebXml.ClassLoaderConfig();
    AppEngineWebXml.ClassLoaderConfig config2 = new AppEngineWebXml.ClassLoaderConfig();
    assertEquals(config1, config2);
    assertNotNull(config1);
    config1.add(ps);
    assertNotEquals(config1, config2);
    config2.add(ps);
    assertEquals(config1, config2);
    assertEquals(1, config1.getEntries().size());
    assertEquals(ps, config1.getEntries().get(0));

    AppEngineWebXml xml1 = new AppEngineWebXml();
    AppEngineWebXml xml2 = new AppEngineWebXml();
    xml1.setClassLoaderConfig(config1);
    assertNotEquals(xml1, xml2);
    xml2.setClassLoaderConfig(config1);
    assertEquals(xml1, xml2);
  }

  public void testClassLoaderConfigToStringAndHash() {
    AppEngineWebXml.PrioritySpecifierEntry ps = new AppEngineWebXml.PrioritySpecifierEntry();
    ps.setFilename("mailapi.jar");
    AppEngineWebXml.ClassLoaderConfig config = new AppEngineWebXml.ClassLoaderConfig();
    config.add(ps);
    AppEngineWebXml xml = new AppEngineWebXml();
    int hash = xml.hashCode();
    String str = xml.toString();
    xml.setClassLoaderConfig(config);
    assertNotEquals(str, xml.toString());
    assertNotEquals(hash, xml.hashCode());
  }

  public void testInstanceClass() {
    AppEngineWebXml xml = new AppEngineWebXml();
    xml.setInstanceClass(WHITE_SPACE);
    assertNull(xml.getInstanceClass());
    xml.setInstanceClass(EMPTY);
    assertNull(xml.getInstanceClass());
    xml.setInstanceClass("F8");
    assertEquals("F8", xml.getInstanceClass());
    xml.setInstanceClass("d4");
    assertEquals("d4", xml.getInstanceClass());
    xml.setInstanceClass(null);
    assertNull(xml.getInstanceClass());
  }

  public void testAutomaticScaling_setMinPendingLatency() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    settings.setMinPendingLatency(WHITE_SPACE);
    assertNull(settings.getMinPendingLatency());
    settings.setMinPendingLatency(EMPTY);
    assertNull(settings.getMinPendingLatency());
    assertTrue(settings.isEmpty());
    settings.setMinPendingLatency(AUTOMATIC);
    assertEquals(AUTOMATIC, settings.getMinPendingLatency());
    assertFalse(settings.isEmpty());
    settings.setMinPendingLatency(null);
    assertNull(settings.getMinPendingLatency());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setMaxPendingLatency() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    settings.setMaxPendingLatency(WHITE_SPACE);
    assertNull(settings.getMaxPendingLatency());
    settings.setMaxPendingLatency(EMPTY);
    assertNull(settings.getMaxPendingLatency());
    assertTrue(settings.isEmpty());
    settings.setMaxPendingLatency(AUTOMATIC);
    assertEquals(AUTOMATIC, settings.getMaxPendingLatency());
    assertFalse(settings.isEmpty());
    settings.setMaxPendingLatency(null);
    assertNull(settings.getMaxPendingLatency());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setMinIdleInstances() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    settings.setMinIdleInstances(WHITE_SPACE);
    assertNull(settings.getMinIdleInstances());
    settings.setMinIdleInstances(EMPTY);
    assertNull(settings.getMinIdleInstances());
    assertTrue(settings.isEmpty());
    settings.setMinIdleInstances(AUTOMATIC);
    assertEquals(AUTOMATIC, settings.getMinIdleInstances());
    assertFalse(settings.isEmpty());
    settings.setMinIdleInstances(null);
    assertNull(settings.getMinIdleInstances());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setMaxIdleInstances() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    settings.setMaxIdleInstances(WHITE_SPACE);
    assertNull(settings.getMaxIdleInstances());
    settings.setMaxIdleInstances(EMPTY);
    assertNull(settings.getMaxIdleInstances());
    assertTrue(settings.isEmpty());
    settings.setMaxIdleInstances(AUTOMATIC);
    assertEquals(AUTOMATIC, settings.getMaxIdleInstances());
    assertFalse(settings.isEmpty());
    settings.setMaxIdleInstances(null);
    assertNull(settings.getMaxIdleInstances());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setMinNumInstances() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    assertTrue(settings.isEmpty());

    settings.setMinNumInstances(null);
    assertNull(settings.getMinNumInstances());
    assertTrue(settings.isEmpty());

    settings.setMinNumInstances(10);
    assertEquals(10, settings.getMinNumInstances().intValue());
    assertFalse(settings.isEmpty());

    settings.setMinNumInstances(null);
    assertNull(settings.getMinNumInstances());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setMaxNumInstances() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    assertTrue(settings.isEmpty());

    settings.setMaxNumInstances(null);
    assertNull(settings.getMaxNumInstances());
    assertTrue(settings.isEmpty());

    settings.setMaxNumInstances(10);
    assertEquals(10, settings.getMaxNumInstances().intValue());
    assertFalse(settings.isEmpty());

    settings.setMaxNumInstances(null);
    assertNull(settings.getMaxNumInstances());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setCoolDownPeriodSec() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    assertTrue(settings.isEmpty());

    settings.setCoolDownPeriodSec(null);
    assertNull(settings.getCoolDownPeriodSec());
    assertTrue(settings.isEmpty());

    settings.setCoolDownPeriodSec(10);
    assertEquals(10, settings.getCoolDownPeriodSec().intValue());
    assertFalse(settings.isEmpty());

    settings.setCoolDownPeriodSec(null);
    assertNull(settings.getCoolDownPeriodSec());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_setCpuUtilization() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    assertTrue(settings.isEmpty());

    settings.setCpuUtilization(null);
    assertNull(settings.getCpuUtilization());
    assertTrue(settings.isEmpty());

    AppEngineWebXml.CpuUtilization expectedCpuUtil = new AppEngineWebXml.CpuUtilization();
    expectedCpuUtil.setTargetUtilization(0.8);
    expectedCpuUtil.setAggregationWindowLengthSec(10);
    settings.setCpuUtilization(expectedCpuUtil);
    AppEngineWebXml.CpuUtilization cpuUtil = settings.getCpuUtilization();
    assertEquals(expectedCpuUtil.getTargetUtilization(), cpuUtil.getTargetUtilization());
    assertEquals(expectedCpuUtil.getAggregationWindowLengthSec(),
        cpuUtil.getAggregationWindowLengthSec());
    assertFalse(settings.isEmpty());

    settings.setCpuUtilization(null);
    assertNull(settings.getCpuUtilization());
    assertTrue(settings.isEmpty());
  }

  public void testAutomaticScaling_hashCode() {
    AppEngineWebXml.AutomaticScaling settings1 = new AppEngineWebXml.AutomaticScaling();
    AppEngineWebXml.AutomaticScaling settings2 = new AppEngineWebXml.AutomaticScaling();
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setMinIdleInstances(AUTOMATIC);
    settings2.setMinIdleInstances(AUTOMATIC);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setMaxIdleInstances("10");
    settings2.setMaxIdleInstances("10");
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setMinPendingLatency("1s");
    settings2.setMinPendingLatency("1s");
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setMaxPendingLatency("1.2s");
    settings2.setMaxPendingLatency("1.2s");
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setMinNumInstances(11);
    settings2.setMinNumInstances(11);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setMaxNumInstances(22);
    settings2.setMaxNumInstances(22);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setCoolDownPeriodSec(33);
    settings2.setCoolDownPeriodSec(33);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    AppEngineWebXml.CpuUtilization cpuUtil = new AppEngineWebXml.CpuUtilization();
    cpuUtil.setTargetUtilization(0.25);
    cpuUtil.setAggregationWindowLengthSec(49);

    settings1.setCpuUtilization(cpuUtil);
    settings2.setCpuUtilization(cpuUtil);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
  }

  public void testAutomaticScaling_equals() {
    AppEngineWebXml.AutomaticScaling settings1 = new AppEngineWebXml.AutomaticScaling();
    AppEngineWebXml.AutomaticScaling settings2 = new AppEngineWebXml.AutomaticScaling();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setMinIdleInstances(AUTOMATIC);
    assertNotEquals(settings1, settings2);
    settings2.setMinIdleInstances(AUTOMATIC);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setMaxIdleInstances("1ms");
    assertNotEquals(settings1, settings2);
    settings2.setMaxIdleInstances("1ms");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setMinPendingLatency("1s");
    assertNotEquals(settings1, settings2);
    settings2.setMinPendingLatency("1s");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setMaxPendingLatency("1.2s");
    assertNotEquals(settings1, settings2);
    settings2.setMaxPendingLatency("1.2s");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setMinNumInstances(5);
    assertNotEquals(settings1, settings2);
    settings2.setMinNumInstances(5);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setMaxNumInstances(10);
    assertNotEquals(settings1, settings2);
    settings2.setMaxNumInstances(10);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setCoolDownPeriodSec(18);
    assertNotEquals(settings1, settings2);
    settings2.setCoolDownPeriodSec(18);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    AppEngineWebXml.CpuUtilization cpuUtil = new AppEngineWebXml.CpuUtilization();
    cpuUtil.setTargetUtilization(0.8);
    cpuUtil.setAggregationWindowLengthSec(25);
    settings1.setCpuUtilization(cpuUtil);
    assertNotEquals(settings1, settings2);
    settings2.setCpuUtilization(cpuUtil);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testAutomaticScaling_toString() {
    AppEngineWebXml.AutomaticScaling settings = new AppEngineWebXml.AutomaticScaling();
    assertEquals("AutomaticScaling [minPendingLatency=null, maxPendingLatency=null, "
            + "minIdleInstances=null, maxIdleInstances=null, maxConcurrentRequests=null, "
            + "minNumInstances=null, maxNumInstances=null, coolDownPeriodSec=null, "
            + "cpuUtilization=null, "
            + "targetNetworkSentBytesPerSec=null, targetNetworkSentPacketsPerSec=null, "
            + "targetNetworkReceivedBytesPerSec=null, targetNetworkReceivedPacketsPerSec=null, "
            + "targetDiskWriteBytesPerSec=null, targetDiskWriteOpsPerSec=null, "
            + "targetDiskReadBytesPerSec=null, targetDiskReadOpsPerSec=null, "
            + "targetRequestCountPerSec=null, targetConcurrentRequests=null"
            + "]",
        settings.toString());
    settings.setMinIdleInstances(AUTOMATIC);
    assertTrue(settings.toString().contains("minIdleInstances=automatic"));
    settings.setMaxIdleInstances("1ms");
    assertTrue(settings.toString().contains("maxIdleInstances=1ms"));
    settings.setMinPendingLatency("1s");
    assertTrue(settings.toString().contains("minPendingLatency=1s"));
    settings.setMaxPendingLatency("1.2s");
    assertTrue(settings.toString().contains("maxPendingLatency=1.2s"));
    settings.setMaxConcurrentRequests("20");
    assertTrue(settings.toString().contains("maxConcurrentRequests=20"));
    settings.setMinNumInstances(1);
    assertTrue(settings.toString().contains("minNumInstances=1"));
    settings.setMaxNumInstances(5);
    assertTrue(settings.toString().contains("maxNumInstances=5"));
    settings.setCoolDownPeriodSec(10);
    assertTrue(settings.toString().contains("coolDownPeriodSec=10"));

    AppEngineWebXml.CpuUtilization cpuUtil = new AppEngineWebXml.CpuUtilization();
    cpuUtil.setTargetUtilization(0.8);
    cpuUtil.setAggregationWindowLengthSec(25);
    settings.setCpuUtilization(cpuUtil);
    assertTrue(settings.toString().contains(
        "cpuUtilization=CpuUtilization [targetUtilization=0.8, aggregationWindowLengthSec=25]"));

    settings.setTargetNetworkSentBytesPerSec(13);
    assertTrue(settings.toString().contains("targetNetworkSentBytesPerSec=13"));
    settings.setTargetNetworkSentPacketsPerSec(14);
    assertTrue(settings.toString().contains("targetNetworkSentPacketsPerSec=14"));
    settings.setTargetNetworkReceivedBytesPerSec(15);
    assertTrue(settings.toString().contains("targetNetworkReceivedBytesPerSec=15"));
    settings.setTargetNetworkReceivedPacketsPerSec(16);
    assertTrue(settings.toString().contains("targetNetworkReceivedPacketsPerSec=16"));
    settings.setTargetDiskWriteBytesPerSec(17);
    assertTrue(settings.toString().contains("targetDiskWriteBytesPerSec=17"));
    settings.setTargetDiskWriteOpsPerSec(18);
    assertTrue(settings.toString().contains("targetDiskWriteOpsPerSec=18"));
    settings.setTargetDiskReadBytesPerSec(19);
    assertTrue(settings.toString().contains("targetDiskReadBytesPerSec=19"));
    settings.setTargetDiskReadOpsPerSec(20);
    assertTrue(settings.toString().contains("targetDiskReadOpsPerSec=20"));
    settings.setTargetRequestCountPerSec(21);
    assertTrue(settings.toString().contains("targetRequestCountPerSec=21"));
    settings.setTargetConcurrentRequests(22);
    assertTrue(settings.toString().contains("targetConcurrentRequests=22"));
  }

  public void testCpuUtilization_setTargetUtilization() {
    AppEngineWebXml.CpuUtilization settings = new AppEngineWebXml.CpuUtilization();
    assertTrue(settings.isEmpty());

    settings.setTargetUtilization(null);
    assertNull(settings.getTargetUtilization());
    assertTrue(settings.isEmpty());

    settings.setTargetUtilization(10.0);
    assertEquals(10, settings.getTargetUtilization().intValue());
    assertFalse(settings.isEmpty());

    settings.setTargetUtilization(null);
    assertNull(settings.getTargetUtilization());
    assertTrue(settings.isEmpty());
  }

  public void testCpuUtilization_setAggregationWindowLengthSec() {
    AppEngineWebXml.CpuUtilization settings = new AppEngineWebXml.CpuUtilization();
    assertTrue(settings.isEmpty());

    settings.setAggregationWindowLengthSec(null);
    assertNull(settings.getAggregationWindowLengthSec());
    assertTrue(settings.isEmpty());

    settings.setAggregationWindowLengthSec(10);
    assertEquals(10, settings.getAggregationWindowLengthSec().intValue());
    assertFalse(settings.isEmpty());

    settings.setAggregationWindowLengthSec(null);
    assertNull(settings.getAggregationWindowLengthSec());
    assertTrue(settings.isEmpty());
  }

  public void testCpuUtilization_hashCode() {
    AppEngineWebXml.CpuUtilization settings1 = new AppEngineWebXml.CpuUtilization();
    AppEngineWebXml.CpuUtilization settings2 = new AppEngineWebXml.CpuUtilization();
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setTargetUtilization(0.5);
    settings2.setTargetUtilization(0.5);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setAggregationWindowLengthSec(10);
    settings2.setAggregationWindowLengthSec(10);
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
  }

  public void testCpuUtilization_equals() {
    AppEngineWebXml.CpuUtilization settings1 = new AppEngineWebXml.CpuUtilization();
    AppEngineWebXml.CpuUtilization settings2 = new AppEngineWebXml.CpuUtilization();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setTargetUtilization(0.5);
    assertNotEquals(settings1, settings2);
    settings2.setTargetUtilization(0.5);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setAggregationWindowLengthSec(10);
    assertNotEquals(settings1, settings2);
    settings2.setAggregationWindowLengthSec(10);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testCpuUtilization_toString() {
    AppEngineWebXml.CpuUtilization settings = new AppEngineWebXml.CpuUtilization();
    assertEquals("CpuUtilization [targetUtilization=null, aggregationWindowLengthSec=null]",
        settings.toString());

    settings.setTargetUtilization(0.5);
    assertEquals("CpuUtilization [targetUtilization=0.5, aggregationWindowLengthSec=null]",
        settings.toString());

    settings.setAggregationWindowLengthSec(10);
    assertEquals("CpuUtilization [targetUtilization=0.5, aggregationWindowLengthSec=10]",
        settings.toString());
  }

  public void testManualScaling_setInstances() {
    AppEngineWebXml.ManualScaling settings = new AppEngineWebXml.ManualScaling();
    settings.setInstances(WHITE_SPACE);
    assertNull(settings.getInstances());
    settings.setInstances(EMPTY);
    assertNull(settings.getInstances());
    assertTrue(settings.isEmpty());
    settings.setInstances("10");
    assertEquals("10", settings.getInstances());
    assertFalse(settings.isEmpty());
    settings.setInstances(null);
    assertNull(settings.getInstances());
    assertTrue(settings.isEmpty());
  }

  public void testManualScaling_hashCode() {
    AppEngineWebXml.ManualScaling settings1 = new AppEngineWebXml.ManualScaling();
    AppEngineWebXml.ManualScaling settings2 = new AppEngineWebXml.ManualScaling();
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setInstances("20");
    settings2.setInstances(settings1.getInstances());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
  }

  public void testManualScaling_equals() {
    AppEngineWebXml.ManualScaling settings1 = new AppEngineWebXml.ManualScaling();
    AppEngineWebXml.ManualScaling settings2 = new AppEngineWebXml.ManualScaling();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setInstances("30");
    assertNotEquals(settings1, settings2);
    settings1.setInstances("");
    assertEquals(settings1, settings2);
    settings1.setInstances("25");
    assertNotEquals(settings1, settings2);
    settings2.setInstances(settings1.getInstances());
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testManualScaling_toString() {
    AppEngineWebXml.ManualScaling settings = new AppEngineWebXml.ManualScaling();
    assertEquals("ManualScaling [instances=null]", settings.toString());
    settings.setInstances("30");
    assertEquals("ManualScaling [instances=30]", settings.toString());
  }

  public void testBasicScaling_setMaxInstances() {
    AppEngineWebXml.BasicScaling settings = new AppEngineWebXml.BasicScaling();
    settings.setMaxInstances(WHITE_SPACE);
    assertNull(settings.getMaxInstances());
    settings.setMaxInstances(EMPTY);
    assertNull(settings.getMaxInstances());
    assertTrue(settings.isEmpty());
    settings.setMaxInstances("10");
    assertEquals("10", settings.getMaxInstances());
    assertFalse(settings.isEmpty());
    settings.setMaxInstances(null);
    assertNull(settings.getMaxInstances());
    assertTrue(settings.isEmpty());
  }

  public void testBasicScaling_setIdleTimeout() {
    AppEngineWebXml.BasicScaling settings = new AppEngineWebXml.BasicScaling();
    settings.setIdleTimeout(WHITE_SPACE);
    assertNull(settings.getIdleTimeout());
    settings.setIdleTimeout(EMPTY);
    assertNull(settings.getIdleTimeout());
    assertTrue(settings.isEmpty());
    settings.setIdleTimeout("10m");
    assertEquals("10m", settings.getIdleTimeout());
    assertFalse(settings.isEmpty());
    settings.setIdleTimeout(null);
    assertNull(settings.getIdleTimeout());
    assertTrue(settings.isEmpty());
  }

  public void testBasicScaling_hashCode() {
    AppEngineWebXml.BasicScaling settings1 = new AppEngineWebXml.BasicScaling();
    AppEngineWebXml.BasicScaling settings2 = new AppEngineWebXml.BasicScaling();
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setMaxInstances("20");
    settings2.setMaxInstances(settings1.getMaxInstances());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
    settings1.setIdleTimeout("20m");
    settings2.setIdleTimeout(settings1.getIdleTimeout());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
  }

  public void testBasicScaling_equals() {
    AppEngineWebXml.BasicScaling settings1 = new AppEngineWebXml.BasicScaling();
    AppEngineWebXml.BasicScaling settings2 = new AppEngineWebXml.BasicScaling();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setMaxInstances("30");
    assertNotEquals(settings1, settings2);
    settings1.setMaxInstances("");
    assertEquals(settings1, settings2);
    settings1.setMaxInstances("25");
    assertNotEquals(settings1, settings2);
    settings2.setMaxInstances(settings1.getMaxInstances());
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
    settings1.setIdleTimeout("30m");
    assertNotEquals(settings1, settings2);
    settings1.setIdleTimeout("");
    assertEquals(settings1, settings2);
    settings1.setIdleTimeout("25m");
    assertNotEquals(settings1, settings2);
    settings2.setIdleTimeout(settings1.getIdleTimeout());
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testBasicScaling_toString() {
    AppEngineWebXml.BasicScaling settings = new AppEngineWebXml.BasicScaling();
    assertEquals("BasicScaling [maxInstances=null, idleTimeout=null]", settings.toString());
    settings.setIdleTimeout("30m");
    assertEquals("BasicScaling [maxInstances=null, idleTimeout=30m]", settings.toString());
    settings.setMaxInstances("30");
    assertEquals("BasicScaling [maxInstances=30, idleTimeout=30m]", settings.toString());
  }

  public void testHealthCheck_setEnableHealthCheck() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.getEnableHealthCheck());

    settings.setEnableHealthCheck(false);
    assertFalse(settings.getEnableHealthCheck());

    settings.setEnableHealthCheck(true);
    assertTrue(settings.getEnableHealthCheck());
  }

  public void testHealthCheck_setCheckIntervalSec() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.isEmpty());
    settings.setCheckIntervalSec(null);
    assertNull(settings.getCheckIntervalSec());
    assertTrue(settings.isEmpty());
    settings.setCheckIntervalSec(10);
    assertEquals(10, settings.getCheckIntervalSec().intValue());
    assertFalse(settings.isEmpty());
    settings.setCheckIntervalSec(null);
    assertNull(settings.getCheckIntervalSec());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_setTimeoutSec() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.isEmpty());
    settings.setTimeoutSec(null);
    assertNull(settings.getTimeoutSec());
    assertTrue(settings.isEmpty());
    settings.setTimeoutSec(10);
    assertEquals(10, settings.getTimeoutSec().intValue());
    assertFalse(settings.isEmpty());
    settings.setTimeoutSec(null);
    assertNull(settings.getTimeoutSec());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_setUnhealthyThreshold() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.isEmpty());
    settings.setUnhealthyThreshold(null);
    assertNull(settings.getUnhealthyThreshold());
    assertTrue(settings.isEmpty());
    settings.setUnhealthyThreshold(10);
    assertEquals(10, settings.getUnhealthyThreshold().intValue());
    assertFalse(settings.isEmpty());
    settings.setUnhealthyThreshold(null);
    assertNull(settings.getUnhealthyThreshold());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_setHealthyThreshold() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.isEmpty());
    settings.setHealthyThreshold(null);
    assertNull(settings.getHealthyThreshold());
    assertTrue(settings.isEmpty());
    settings.setHealthyThreshold(10);
    assertEquals(10, settings.getHealthyThreshold().intValue());
    assertFalse(settings.isEmpty());
    settings.setHealthyThreshold(null);
    assertNull(settings.getHealthyThreshold());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_setRestartThreshold() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    assertTrue(settings.isEmpty());
    settings.setRestartThreshold(null);
    assertNull(settings.getRestartThreshold());
    assertTrue(settings.isEmpty());
    settings.setRestartThreshold(10);
    assertEquals(10, settings.getRestartThreshold().intValue());
    assertFalse(settings.isEmpty());
    settings.setRestartThreshold(null);
    assertNull(settings.getRestartThreshold());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_setHost() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();
    settings.setHost(WHITE_SPACE);
    assertNull(settings.getHost());
    assertTrue(settings.isEmpty());
    settings.setHost(EMPTY);
    assertNull(settings.getHost());
    assertTrue(settings.isEmpty());
    settings.setHost("test.com");
    assertEquals("test.com", settings.getHost());
    assertFalse(settings.isEmpty());
    settings.setHost(null);
    assertNull(settings.getHost());
    assertTrue(settings.isEmpty());
  }

  public void testHealthCheck_hashCode() {
    AppEngineWebXml.HealthCheck settings1 = new AppEngineWebXml.HealthCheck();
    AppEngineWebXml.HealthCheck settings2 = new AppEngineWebXml.HealthCheck();
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setEnableHealthCheck(true);
    settings2.setEnableHealthCheck(settings1.getEnableHealthCheck());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setEnableHealthCheck(false);
    settings2.setEnableHealthCheck(settings1.getEnableHealthCheck());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setCheckIntervalSec(5);
    settings2.setCheckIntervalSec(settings1.getCheckIntervalSec());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setTimeoutSec(6);
    settings2.setTimeoutSec(settings1.getTimeoutSec());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setUnhealthyThreshold(7);
    settings2.setUnhealthyThreshold(settings1.getUnhealthyThreshold());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setHealthyThreshold(8);
    settings2.setHealthyThreshold(settings1.getHealthyThreshold());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setRestartThreshold(9);
    settings2.setRestartThreshold(settings1.getRestartThreshold());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());

    settings1.setHost("test.com");
    settings2.setHost(settings1.getHost());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    assertEquals(settings1.hashCode(), settings1.hashCode());
  }

  public void testHealthCheck_equals() {
    AppEngineWebXml.HealthCheck settings1 = new AppEngineWebXml.HealthCheck();
    AppEngineWebXml.HealthCheck settings2 = new AppEngineWebXml.HealthCheck();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setEnableHealthCheck.
    settings2.setEnableHealthCheck(true);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setEnableHealthCheck(false);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setEnableHealthCheck(false);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setCheckIntervalSec.
    settings1.setCheckIntervalSec(null);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setCheckIntervalSec(5);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setCheckIntervalSec(5);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setTimeoutSec.
    settings1.setTimeoutSec(null);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setTimeoutSec(6);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setTimeoutSec(6);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setUnhealthyThreshold.
    settings1.setUnhealthyThreshold(null);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setUnhealthyThreshold(7);
    assertNotEquals(settings1, settings2);

    settings2.setUnhealthyThreshold(7);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setHealthyThreshold.
    settings1.setHealthyThreshold(null);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setHealthyThreshold(8);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setHealthyThreshold(8);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setRestartThreshold.
    settings1.setRestartThreshold(null);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setRestartThreshold(9);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setRestartThreshold(9);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setHost.
    settings1.setHost("");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    settings1.setHost("test.com");
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setHost("test.com");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testHealthCheck_toString() {
    AppEngineWebXml.HealthCheck settings = new AppEngineWebXml.HealthCheck();

    assertEquals("HealthCheck [enableHealthCheck=true, checkIntervalSec=null, "
        + "timeoutSec=null, unhealthyThreshold=null, healthyThreshold=null, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setEnableHealthCheck(false);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=null, "
        + "timeoutSec=null, unhealthyThreshold=null, healthyThreshold=null, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setCheckIntervalSec(5);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=null, unhealthyThreshold=null, healthyThreshold=null, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setTimeoutSec(6);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=6, unhealthyThreshold=null, healthyThreshold=null, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setUnhealthyThreshold(7);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=6, unhealthyThreshold=7, healthyThreshold=null, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setHealthyThreshold(8);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=6, unhealthyThreshold=7, healthyThreshold=8, "
        + "restartThreshold=null, host=null]", settings.toString());

    settings.setRestartThreshold(9);
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=6, unhealthyThreshold=7, healthyThreshold=8, "
        + "restartThreshold=9, host=null]", settings.toString());

    settings.setHost("test.com");
    assertEquals("HealthCheck [enableHealthCheck=false, checkIntervalSec=5, "
        + "timeoutSec=6, unhealthyThreshold=7, healthyThreshold=8, "
        + "restartThreshold=9, host=test.com]", settings.toString());
  }

  public void testResources_setCpu() {
    AppEngineWebXml.Resources resources = new AppEngineWebXml.Resources();
    resources.setCpu(3.1);
    assertEquals(3.1, resources.getCpu());
  }

  public void testResources_setMemoryGb() {
    AppEngineWebXml.Resources resources = new AppEngineWebXml.Resources();
    resources.setMemoryGb(31.0);
    assertEquals(31.0, resources.getMemoryGb());
  }

  public void testResources_setDiskSizeGb() {
    AppEngineWebXml.Resources resources = new AppEngineWebXml.Resources();
    resources.setDiskSizeGb(310);
    assertEquals(310, resources.getDiskSizeGb());
  }

  public void testResources_hashCode() {
    AppEngineWebXml.Resources settings1 = new AppEngineWebXml.Resources();
    AppEngineWebXml.Resources settings2 = new AppEngineWebXml.Resources();

    assertEquals(settings1.hashCode(), settings2.hashCode());
    settings1.setCpu(3.1);
    settings2.setCpu(settings1.getCpu());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    settings1.setMemoryGb(27.0);
    settings2.setMemoryGb(settings1.getMemoryGb());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    settings1.setDiskSizeGb(312);
    settings2.setDiskSizeGb(settings1.getDiskSizeGb());
    assertEquals(settings1.hashCode(), settings2.hashCode());
  }

  public void testResources_equals() {
    AppEngineWebXml.Resources settings1 = new AppEngineWebXml.Resources();
    AppEngineWebXml.Resources settings2 = new AppEngineWebXml.Resources();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setCpu.
    settings1.setCpu(3.1);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setCpu(3.1);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setMemoryGb.
    settings1.setMemoryGb(3.1);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setMemoryGb(3.1);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setDiskSizeGb.
    settings1.setDiskSizeGb(31);
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setDiskSizeGb(31);
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testResources_toString() {
    AppEngineWebXml.Resources resources = new AppEngineWebXml.Resources();

    assertEquals("Resources [cpu=0.0, memoryGb=0.0, "
        + "diskSizeGb=0]", resources.toString());

    resources.setCpu(1.6);
    assertEquals("Resources [cpu=1.6, memoryGb=0.0, "
        + "diskSizeGb=0]", resources.toString());

    resources.setMemoryGb(5.0);
    assertEquals("Resources [cpu=1.6, memoryGb=5.0, "
        + "diskSizeGb=0]", resources.toString());
    resources.setDiskSizeGb(60);
    assertEquals("Resources [cpu=1.6, memoryGb=5.0, "
        + "diskSizeGb=60]", resources.toString());
  }

  public void testNetwork_setInstanceTag() {
    AppEngineWebXml.Network network = new AppEngineWebXml.Network();
    network.setInstanceTag("mytag");
    assertEquals("mytag", network.getInstanceTag());
  }

  public void testNetwork_addForwardedPort() {
    AppEngineWebXml.Network network = new AppEngineWebXml.Network();
    network.addForwardedPort("myport");
    assertEquals("myport", network.getForwardedPorts().get(0));
  }

  public void testNetwork_hashCode() {
    AppEngineWebXml.Network settings1 = new AppEngineWebXml.Network();
    AppEngineWebXml.Network settings2 = new AppEngineWebXml.Network();

    assertEquals(settings1.hashCode(), settings2.hashCode());
    settings1.setInstanceTag("mytag");
    settings2.setInstanceTag(settings1.getInstanceTag());
    assertEquals(settings1.hashCode(), settings2.hashCode());
    settings1.addForwardedPort("myport");
    settings2.addForwardedPort(settings1.getForwardedPorts().get(0));
    assertEquals(settings1.hashCode(), settings2.hashCode());
  }

  public void testNetwork_equals() {
    AppEngineWebXml.Network settings1 = new AppEngineWebXml.Network();
    AppEngineWebXml.Network settings2 = new AppEngineWebXml.Network();
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For setInstanceTag.
    settings1.setInstanceTag("mytag");
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.setInstanceTag("mytag");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);

    // For addForwardedPort.
    settings1.addForwardedPort("myport");
    assertNotEquals(settings1, settings2);
    assertNotEquals(settings1, settings2);

    settings2.addForwardedPort("myport");
    assertEquals(settings1, settings2);
    assertEquals(settings1, settings1);
  }

  public void testNetwork_toString() {
    AppEngineWebXml.Network network = new AppEngineWebXml.Network();

    assertEquals("Network [forwardedPorts=[], instanceTag=null]", network.toString());

    network.setInstanceTag("mytag");
    assertEquals("Network [forwardedPorts=[], instanceTag=mytag]", network.toString());

    network.addForwardedPort("myport");
    assertEquals("Network [forwardedPorts=[myport], instanceTag=mytag]", network.toString());
  }

  public void testGetScalingType_automatic() {
    AppEngineWebXml webXml = new AppEngineWebXml();
    assertEquals(ScalingType.AUTOMATIC, webXml.getScalingType());
  }

  public void testGetScalingType_manual() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.getManualScaling().setInstances("3");
    assertEquals(ScalingType.MANUAL, appEngineWebXml.getScalingType());
  }

  public void testGetScalingType_basic() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.getBasicScaling().setMaxInstances("3");
    assertEquals(ScalingType.BASIC, appEngineWebXml.getScalingType());
  }
}
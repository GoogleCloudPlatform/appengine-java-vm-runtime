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

import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link AppEngineWebXmlProcessor}.
 */
public class AppEngineWebXmlProcessorTest extends TestCase {

  private static final String XML_FORMAT =
      " <appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">"
          + "<application><!-- ignore -->helloworld-java<!-- ignore as well--></application> "
          + "</appengine-web-app>";

  private static final String XML_FORMAT_SESSIONS_SSL =
      " <appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">"
          + "<application>helloworld-java</application> "
          + "<version>1</version> "
          + "<ssl-enabled>true</ssl-enabled>"
          + "<sessions-enabled>true</sessions-enabled>"
          + "</appengine-web-app>";

  private AppEngineWebXml getAppEngineWebXml(String content) {
    File schemaFile =
        new File("src/test/resources/com/google/apphosting/utils/config/appengine-web.xsd");
    XmlUtils.validateXmlContent(content, schemaFile);
    return new AppEngineWebXmlProcessor()
        .processXml(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  public void testParseGoodXml() {
    AppEngineWebXml aewx = getAppEngineWebXml(XML_FORMAT);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId(null);
    expected.setSslEnabled(true);
    assertEquals(expected, aewx);
  }

  private AppEngineWebXml newAppEngineWebXml() {
    AppEngineWebXml result = new AppEngineWebXml();
    result.setWarmupRequestsEnabled(true);
    return result;
  }

  public void testParseSessionSslXml() {
    AppEngineWebXml aewx = getAppEngineWebXml(XML_FORMAT_SESSIONS_SSL);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setSslEnabled(true);
    expected.setSessionsEnabled(true);
    assertEquals(expected, aewx);
  }

  public void testAsyncSessionPersistence() {
    doTestAsyncSessionPersistence("queue-name=\"blar\"", "blar");
  }

  public void testAsyncSessionPersistence_NoQueueName() {
    doTestAsyncSessionPersistence("", null);
  }

  private void doTestAsyncSessionPersistence(String queueNameXml, String expectedQueueName) {
    String xml = String.format(
        " <appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">"
            + "<application>helloworld-java</application> "
            + "<version>1</version> "
            + "<sessions-enabled>true</sessions-enabled>"
            + "<async-session-persistence enabled=\"true\" %s/>"
            + "</appengine-web-app>", queueNameXml);
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    assertFalse(expected.getAsyncSessionPersistence());
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setSslEnabled(true);
    expected.setSessionsEnabled(true);
    expected.setAsyncSessionPersistence(true);
    expected.setAsyncSessionPersistenceQueueName(expectedQueueName);
    assertEquals(expected, aewx);
  }

  public void testSslDisabled() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<ssl-enabled>false</ssl-enabled>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setSslEnabled(false);
    assertEquals(expected, aewx);
  }

  public void testServerAndInstance() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<service>service1</service>\n"
            + "<version>1</version>\n"
            + "<instance-class>F4</instance-class>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setService("service1");
    expected.setInstanceClass("F4");
    assertEquals(expected, aewx);
  }

  public void testModuleIsAllowed() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<module>service1</module>\n"
            + "<version>1</version>\n"
            + "<instance-class>F4</instance-class>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setModule("service1");
    expected.setInstanceClass("F4");
    assertEquals(expected, aewx);
    assertEquals("service1", aewx.getModule());
  }

  public void testErrorWhenServiceAndModuleAreDefined() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<module>service1</module>\n"
            + "<service>service2</service>\n"
            + "<version>1</version>\n"
            + "<instance-class>F4</instance-class>\n"
            + "</appengine-web-app>\n";

    try {
      getAppEngineWebXml(xml);
      fail("Expected AppEngineConfigException.");
    } catch (AppEngineConfigException ex) {
      // expected
    }
  }

  public void testParseUserPermissions() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<user-permissions>\n"
            + "<permission class=\"com.foo.FooPermission\" name=\"foo\"/>\n"
            + "<permission class=\"com.foo.FooPermission\" name=\"bar\" actions=\"baz,qux\"/>\n"
            + "</user-permissions>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.addUserPermission("com.foo.FooPermission", "foo", null);
    expected.addUserPermission("com.foo.FooPermission", "bar", "baz,qux");
    assertEquals(expected, aewx);
  }

  public void testParseInvalidUserPermissions() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<user-permissions>\n"
            + "<permission class=\"java.io.FilePermission\" name=\"-\" actions=\"read\"/>\n"
            + "</user-permissions>\n"
            + "</appengine-web-app>\n";

    try {
      getAppEngineWebXml(xml);
      fail("Expected AppEngineConfigException.");
    } catch (AppEngineConfigException ex) {
      // expected
    }
  }

  public void testParseBadXml() {
    String badXml =
        " <appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">"
            + "<blarg></notblarg>"
            + "</appengine-web-app>";

    try {
      getAppEngineWebXml(badXml);
      fail("expected rte");
    } catch (AppEngineConfigException rte) {
      // good
    }
  }

  public void testParsePublicRoot() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<public-root>/foobar</public-root>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setPublicRoot("/foobar");
    assertEquals(expected, aewx);
  }

  public void testParseInvalidPublicRoot() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<public-root>*.html</public-root>\n"
            + "</appengine-web-app>\n";

    try {
      getAppEngineWebXml(xml);
      fail("Expected AppEngineConfigException.");
    } catch (AppEngineConfigException ex) {
      // expected
    }
  }

  public void testParseNoInboundServices() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<inbound-services/>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertEquals(Collections.singleton("warmup"), aewx.getInboundServices());
  }

  public void testParseOneInboundService() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<inbound-services>\n"
            + "  <service>xmpp_message</service>\n"
            + "</inbound-services>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    Set<String> expected = new HashSet<>();
    expected.add("warmup");
    expected.add("xmpp_message");
    assertEquals(expected, aewx.getInboundServices());
  }

  public void testParseTwoInboundServices() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<inbound-services>\n"
            + "  <service>mail</service>\n"
            + "  <service>xmpp_message</service>\n"
            + "</inbound-services>\n"
            + "</appengine-web-app>\n";

    Set<String> expected = new HashSet<>();
    expected.add("warmup");
    expected.add("mail");
    expected.add("xmpp_message");
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertEquals(expected, aewx.getInboundServices());
  }

  public void testStaticInclude() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<static-files>\n"
            + "  <include path=\"**.nocache.**\" expiration=\"8d\"/>\n"
            + "  <include path=\"**\"/>\n"
            + "</static-files>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertEquals(2, aewx.getStaticFileIncludes().size());
    Iterator<AppEngineWebXml.StaticFileInclude> it = aewx.getStaticFileIncludes().iterator();
    assertEquals(new AppEngineWebXml.StaticFileInclude("**.nocache.**", "8d"), it.next());
    assertEquals(new AppEngineWebXml.StaticFileInclude("**", null), it.next());
  }

  public void testStaticIncludeWithZeroExpiration() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<static-files>\n"
            + "  <include path=\"**.nocache.**\" expiration=\"0\"/>\n"
            + "  <include path=\"**\"/>\n"
            + "</static-files>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertEquals(2, aewx.getStaticFileIncludes().size());
    Iterator<AppEngineWebXml.StaticFileInclude> it = aewx.getStaticFileIncludes().iterator();
    assertEquals(new AppEngineWebXml.StaticFileInclude("**.nocache.**", "0"), it.next());
    assertEquals(new AppEngineWebXml.StaticFileInclude("**", null), it.next());
  }

  public void testStaticIncludeWithHttpHeaders() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>all-your-base</application>\n"
            + "<version>1</version>\n"
            + "<static-files>\n"
            + "  <include path=\"/static-files/*\">\n"
            + "    <http-header name=\"Bomb-Planter\" value=\"somebody\"/>\n"
            + "    <http-header name=\"Chance-To-Survive\" value=\"no\"/>\n"
            + "  </include>\n"
            + "</static-files>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml aewx = getAppEngineWebXml(xml);

    assertEquals(1, aewx.getStaticFileIncludes().size());
    Map<String, String> httpHeaders = aewx.getStaticFileIncludes().get(0).getHttpHeaders();
    assertEquals(2, httpHeaders.size());
    assertEquals("somebody", httpHeaders.get("Bomb-Planter"));
    assertEquals("no", httpHeaders.get("Chance-To-Survive"));
  }

  public void testPrecompilationEnabled() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<precompilation-enabled>true</precompilation-enabled>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setPrecompilationEnabled(true);
    assertEquals(expected, aewx);
  }

  public void testThreadsafe() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<threadsafe>true</threadsafe>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertTrue(aewx.getThreadsafeValueProvided());
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setThreadsafe(true);
    assertEquals(expected, aewx);
  }

  public void testAutoIdPolicy() {
    for (String policy : Arrays.asList("legacy", "default")) {
      String xml =
          "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
              + "<application>helloworld-java</application>\n"
              + "<version>1</version>\n"
              + "<auto-id-policy>" + policy + "</auto-id-policy>"
              + "</appengine-web-app>\n";

      AppEngineWebXml aewx = getAppEngineWebXml(xml);
      assertNotNull(aewx);
      AppEngineWebXml expected = newAppEngineWebXml();
      expected.setAppId("helloworld-java");
      expected.setMajorVersionId("1");
      expected.setAutoIdPolicy(policy);
      assertEquals(expected, aewx);
    }
  }

  public void testCodeLock() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<code-lock>true</code-lock>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setCodeLock(true);
    assertEquals(expected, aewx);
  }

  public void testVmRuntime() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<vm>true</vm>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setUseVm(true);
    assertEquals(expected, aewx);
  }

  public void testEnv() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<env>1</env>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setEnv("1");
    assertEquals(expected, aewx);
  }

  public void testBetaSettings() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<vm>true</vm>\n"
            + "<beta-settings>\n"
            + "  <setting name=\"machine_type\" value=\"n1-standard-1\"/>\n"
            + "  <setting name=\"health_check_enabled\" value=\"on\"/>\n"
            + "  <setting name=\"health_check_restart_timeout\" value=\"600\"/>\n"
            + "  <setting name=\"root_setup_command\" value=\"/bin/bash {app_name}/setup.sh\"/>\n"
            + "</beta-settings>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setUseVm(true);
    expected.addBetaSetting("machine_type", "n1-standard-1");
    expected.addBetaSetting("health_check_enabled", "on");
    expected.addBetaSetting("health_check_restart_timeout", "600");
    expected.addBetaSetting("root_setup_command", "/bin/bash {app_name}/setup.sh");
    assertEquals(expected, aewx);
  }

  public void testAdminConsolePages() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<admin-console>"
            + "<page name=\"foo\" url=\"/bar\"/>"
            + "<page name=\"baz\" url=\"/bax\"/>"
            + "</admin-console>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.addAdminConsolePage(new AdminConsolePage("foo", "/bar"));
    expected.addAdminConsolePage(new AdminConsolePage("baz", "/bax"));
    assertEquals(expected, aewx);
  }

  public void testErrorHandlers() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<static-error-handlers>"
            + "<handler file=\"ohno.html\"/>"
            + "<handler file=\"timeout.html\" error-code=\"timeout\"/>"
            + "</static-error-handlers>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.addErrorHandler(new ErrorHandler("ohno.html", null));
    expected.addErrorHandler(new ErrorHandler("timeout.html", "timeout"));
    assertEquals(expected, aewx);
  }

  public void testWarmupRequestsDefault() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertTrue(aewx.getWarmupRequestsEnabled());
    assertEquals(Collections.singleton("warmup"), aewx.getInboundServices());
  }

  public void testWarmupRequestsTrue() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<warmup-requests-enabled>true</warmup-requests-enabled>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertTrue(aewx.getWarmupRequestsEnabled());
    assertEquals(Collections.singleton("warmup"), aewx.getInboundServices());
  }

  public void testWarmupRequestsFalse() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<warmup-requests-enabled>false</warmup-requests-enabled>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertFalse(aewx.getWarmupRequestsEnabled());
    assertEquals(Collections.emptySet(), aewx.getInboundServices());
  }

  public void testApiConfig() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<api-config servlet-class=\"foo.ConfigServlet\" url-pattern=\"/my/api\">\n"
            + "</api-config>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setApiConfig(new ApiConfig("foo.ConfigServlet", "/my/api"));
    assertEquals(expected, aewx);
  }

  public void testApiEndpoint() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<api-config servlet-class=\"foo.ConfigServlet\" url-pattern=\"/my/api\">\n"
            + "<endpoint-servlet-mapping-id>endpoint-1</endpoint-servlet-mapping-id>\n"
            + "</api-config>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setApiConfig(new ApiConfig("foo.ConfigServlet", "/my/api"));
    expected.addApiEndpoint("endpoint-1");
    assertTrue(aewx.isApiEndpoint("endpoint-1"));
    assertEquals(expected, aewx);
  }

  public void testMultipleApiEndpoints() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<api-config servlet-class=\"foo.ConfigServlet\" url-pattern=\"/my/api\">\n"
            + "<endpoint-servlet-mapping-id>endpoint-1</endpoint-servlet-mapping-id>\n"
            + "<endpoint-servlet-mapping-id>endpoint-foo</endpoint-servlet-mapping-id>\n"
            + "<endpoint-servlet-mapping-id>endpoint-bar</endpoint-servlet-mapping-id>\n"
            + "</api-config>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setApiConfig(new ApiConfig("foo.ConfigServlet", "/my/api"));
    expected.addApiEndpoint("endpoint-1");
    assertTrue(aewx.isApiEndpoint("endpoint-1"));
    expected.addApiEndpoint("endpoint-foo");
    assertTrue(aewx.isApiEndpoint("endpoint-foo"));
    expected.addApiEndpoint("endpoint-bar");
    assertTrue(aewx.isApiEndpoint("endpoint-bar"));
    assertEquals(expected, aewx);
  }

  public void testWhitespaceIsTrimmed() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<public-root>\n\n \n/root\n \n </public-root>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertEquals("/root", aewx.getPublicRoot());
  }

  private AppEngineWebXml.AutomaticScaling parseAndGetAutomaticScaling(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getAutomaticScaling());
    AppEngineWebXml.AutomaticScaling settings = aewx.getAutomaticScaling();
    assertTrue(aewx.getManualScaling().isEmpty());
    assertTrue(aewx.getBasicScaling().isEmpty());
    return settings;
  }

  public void testAutomaticScaling_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getMinPendingLatency());
    assertNull(settings.getMaxPendingLatency());
    assertNull(settings.getMaxIdleInstances());
    assertNull(settings.getMinIdleInstances());
    assertNull(settings.getMinNumInstances());
    assertNull(settings.getMaxNumInstances());
    assertNull(settings.getCoolDownPeriodSec());
    assertNull(settings.getCpuUtilization());
  }

  public void testAutomaticScaling_idleInstancesAutomatic() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-idle-instances>automatic</min-idle-instances>\n"
            + " <max-idle-instances>automatic</max-idle-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("automatic", settings.getMinIdleInstances());
    assertEquals("automatic", settings.getMaxIdleInstances());
  }

  public void testAutomaticScaling_idleInstances() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-idle-instances>2</min-idle-instances>\n"
            + " <max-idle-instances>3</max-idle-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("2", settings.getMinIdleInstances());
    assertEquals("3", settings.getMaxIdleInstances());
  }

  public void testAutomaticScaling_pendingLatencyAutomatic() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-pending-latency>automatic</min-pending-latency>\n"
            + " <max-pending-latency>automatic</max-pending-latency>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("automatic", settings.getMinPendingLatency());
    assertEquals("automatic", settings.getMaxPendingLatency());
  }

  public void testAutomaticScaling_pendingLatency() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-pending-latency>2.2s</min-pending-latency>\n"
            + " <max-pending-latency>3000ms</max-pending-latency>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("2.2s", settings.getMinPendingLatency());
    assertEquals("3000ms", settings.getMaxPendingLatency());
  }

  public void testAutomaticScaling_maxConcurrentRequests() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <max-concurrent-requests>20</max-concurrent-requests>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("20", settings.getMaxConcurrentRequests());
  }

  public void testAutomaticScaling_minNumInstances_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances></min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail("Should fail due to incorrect min-num-instances");
    } catch (AppEngineConfigException expected) {
    }

    xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances>   </min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail("Should fail due to incorrect min-num-instances");
    } catch (AppEngineConfigException expected) {
    }

    xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances> \t \n </min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail("Should fail due to incorrect min-num-instances");
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_minNumInstances_valid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances>2</min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals(2, settings.getMinNumInstances().intValue());
  }

  public void testAutomaticScaling_minNumInstances_zero() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances>0</min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_minNumInstances_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances>-2</min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_minNumInstances_string() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <min-num-instances>123abc</min-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_maxNumInstances_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getMaxNumInstances());
  }

  public void testAutomaticScaling_maxNumInstances_zero() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <max-num-instances>0</max-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_maxNumInstances_valid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <max-num-instances>15</max-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals(15, settings.getMaxNumInstances().intValue());
  }

  public void testAutomaticScaling_maxNumInstances_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <max-num-instances>-5</max-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_maxNumInstances_string() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <max-num-instances>123abc</max-num-instances>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_coolDownPeriodSec_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getCoolDownPeriodSec());

    xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getCoolDownPeriodSec());
  }

  public void testAutomaticScaling_coolDownPeriodSec_valid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cool-down-period-sec>25</cool-down-period-sec>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals(25, settings.getCoolDownPeriodSec().intValue());
  }

  public void testAutomaticScaling_coolDownPeriodSec_zero() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cool-down-period-sec>0</cool-down-period-sec>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_coolDownPeriodSec_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cool-down-period-sec>-25</cool-down-period-sec>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_coolDownPeriodSec_string() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cool-down-period-sec>123abc</cool-down-period-sec>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getCpuUtilization());
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getCpuUtilization());
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_valid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <target-utilization>0.5</target-utilization>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals(0.5, settings.getCpuUtilization().getTargetUtilization().doubleValue());
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_zero() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <target-utilization>0</target-utilization>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <target-utilization>-0.5</target-utilization>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_moreThanOne() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <target-utilization>1.1</target-utilization>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_targetUtilization_string() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <target-utilization>0.5adc</target-utilization>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_aggregationWindowLengthSec_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getCpuUtilization());
  }

  public void testAutomaticScaling_cpuUtilization_aggregationWindowLengthSec_valid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <aggregation-window-length-sec>35</aggregation-window-length-sec>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.AutomaticScaling settings = parseAndGetAutomaticScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals(35, settings.getCpuUtilization().getAggregationWindowLengthSec().intValue());
  }

  public void testAutomaticScaling_cpuUtilization_aggregationWindowLengthSec_zero() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <aggregation-window-length-sec>0</aggregation-window-length-sec>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_aggregationWindowLengthSec_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <aggregation-window-length-sec>-5</aggregation-window-length-sec>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testAutomaticScaling_cpuUtilization_aggregationWindowLengthSec_string() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<automatic-scaling>\n"
            + " <cpu-utilization>\n"
            + "   <aggregation-window-length-sec>5abc</aggregation-window-length-sec>\n"
            + " </cpu-utilization>\n"
            + "</automatic-scaling>\n"
            + "</appengine-web-app>\n";
    try {
      parseAndGetAutomaticScaling(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  private AppEngineWebXml.ManualScaling parseAngGetManualScaling(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getManualScaling());
    AppEngineWebXml.ManualScaling settings = aewx.getManualScaling();
    assertTrue(aewx.getAutomaticScaling().isEmpty());
    assertTrue(aewx.getBasicScaling().isEmpty());
    return settings;
  }

  public void testManualScaling_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<manual-scaling>\n"
            + " <instances>           </instances>\n"
            + "</manual-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.ManualScaling settings = parseAngGetManualScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getInstances());
  }

  public void testManualScaling_instances() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<manual-scaling>\n"
            + " <instances>10</instances>\n"
            + "</manual-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.ManualScaling settings = parseAngGetManualScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("10", settings.getInstances());
  }

  private AppEngineWebXml.BasicScaling parseAngGetBasicScaling(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getBasicScaling());
    AppEngineWebXml.BasicScaling settings = aewx.getBasicScaling();
    assertTrue(aewx.getAutomaticScaling().isEmpty());
    assertTrue(aewx.getManualScaling().isEmpty());
    return settings;
  }

  public void testBasicScaling_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<basic-scaling>\n"
            + " <max-instances>           </max-instances>\n"
            + " <idle-timeout>           </idle-timeout>\n"
            + "</basic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.BasicScaling settings = parseAngGetBasicScaling(xml);
    assertTrue(settings.isEmpty());
    assertNull(settings.getIdleTimeout());
  }

  public void testBasicScaling_maxInstances() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<basic-scaling>\n"
            + " <max-instances>11</max-instances>\n"
            + "</basic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.BasicScaling settings = parseAngGetBasicScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("11", settings.getMaxInstances());
    assertNull(settings.getIdleTimeout());
  }

  public void testBasicScaling_maxInstancesAndIdleTimeout() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<basic-scaling>\n"
            + " <max-instances>11</max-instances>\n"
            + " <idle-timeout>10m</idle-timeout>\n"
            + "</basic-scaling>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.BasicScaling settings = parseAngGetBasicScaling(xml);
    assertFalse(settings.isEmpty());
    assertEquals("11", settings.getMaxInstances());
    assertEquals("10m", settings.getIdleTimeout());
  }

  private AppEngineWebXml.HealthCheck parseAngGetHealthCheck(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getHealthCheck());
    AppEngineWebXml.HealthCheck settings = aewx.getHealthCheck();
    return settings;
  }

  public void testHealthCheck_noSetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertTrue(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_emptySetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertTrue(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_enableHealthCheck_noSetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertTrue(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_enableHealthCheck_empty() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertTrue(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_enableHealthCheck_false() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <enable-health-check>false</enable-health-check>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertFalse(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_enableHealthCheck_true() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <enable-health-check>true</enable-health-check>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertTrue(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getCheckIntervalSec() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <check-interval-sec>5</check-interval-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertEquals(5, settings.getCheckIntervalSec().intValue());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getCheckIntervalSec_invalid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <check-interval-sec>123abc</check-interval-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getCheckIntervalSec_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <check-interval-sec>-1</check-interval-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getTimeoutSec() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <timeout-sec>6</timeout-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertEquals(6, settings.getTimeoutSec().intValue());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getTimeoutSec_invalid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <timeout-sec>123abc</timeout-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getTimeoutSec_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <timeout-sec>-1</timeout-sec>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getUnhealthyThreshold() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <unhealthy-threshold>7</unhealthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertEquals(7, settings.getUnhealthyThreshold().intValue());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getUnhealthyThreshold_invalid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <unhealthy-threshold>123abc</unhealthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getUnhealthyThreshold_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <unhealthy-threshold>-1</unhealthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getHealthyThreshold() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <healthy-threshold>8</healthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertEquals(8, settings.getHealthyThreshold().intValue());
    assertNull(settings.getRestartThreshold());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getHealthyThreshold_invalid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <healthy-threshold>123abc</healthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getHealthyThreshold_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <healthy-threshold>-1</healthy-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getRestartThreshold() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <restart-threshold>9</restart-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertEquals(9, settings.getRestartThreshold().intValue());
    assertNull(settings.getHost());
  }

  public void testHealthCheck_getRestartThreshold_invalid() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <restart-threshold>123abc</restart-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getRestartThreshold_negative() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <restart-threshold>-1</restart-threshold>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";

    try {
      parseAngGetHealthCheck(xml);
      fail();
    } catch (AppEngineConfigException expected) {
    }
  }

  public void testHealthCheck_getHost() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <host>test.com</host>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertTrue(settings.getEnableHealthCheck());
    assertNull(settings.getCheckIntervalSec());
    assertNull(settings.getTimeoutSec());
    assertNull(settings.getUnhealthyThreshold());
    assertNull(settings.getHealthyThreshold());
    assertNull(settings.getRestartThreshold());
    assertEquals("test.com", settings.getHost());
  }

  public void testHealthCheck_allSettings() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<health-check>\n"
            + " <enable-health-check>false</enable-health-check>\n"
            + " <check-interval-sec>5</check-interval-sec>\n"
            + " <timeout-sec>6</timeout-sec>\n"
            + " <unhealthy-threshold>7</unhealthy-threshold>\n"
            + " <healthy-threshold>8</healthy-threshold>\n"
            + " <restart-threshold>9</restart-threshold>\n"
            + " <host>test.com</host>\n"
            + "</health-check>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.HealthCheck settings = parseAngGetHealthCheck(xml);
    assertFalse(settings.isEmpty());
    assertFalse(settings.getEnableHealthCheck());
    assertEquals(5, settings.getCheckIntervalSec().intValue());
    assertEquals(6, settings.getTimeoutSec().intValue());
    assertEquals(7, settings.getUnhealthyThreshold().intValue());
    assertEquals(8, settings.getHealthyThreshold().intValue());
    assertEquals(9, settings.getRestartThreshold().intValue());
    assertEquals("test.com", settings.getHost());
  }

  private AppEngineWebXml.Resources parseAndGetResources(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getResources());
    AppEngineWebXml.Resources resources = aewx.getResources();
    return resources;
  }

  private AppEngineWebXml.Network parseAndGetNetwork(String xml) {
    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    assertNotNull(aewx.getNetwork());
    AppEngineWebXml.Network network = aewx.getNetwork();
    return network;
  }

  public void testResources_NoSetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertTrue(resources.isEmpty());
  }

  public void testResources_EmptySetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<resources>\n"
            + "</resources>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertTrue(resources.isEmpty());
  }

  public void testResources_Cpu() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<resources>\n"
            + "<cpu>2.5</cpu>\n"
            + "</resources>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertEquals(resources.getCpu(), 2.5);
  }

  public void testResources_MemoryGb() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<resources>\n"
            + "<memory-gb>25</memory-gb>\n"
            + "</resources>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertEquals(resources.getMemoryGb(), 25.0);
  }

  public void testResources_DiskSizeGb() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<resources>\n"
            + "<disk-size-gb>250</disk-size-gb>\n"
            + "</resources>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertEquals(resources.getDiskSizeGb(), 250);
  }

  public void testResources_Full() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<resources>\n"
            + "<cpu>2.5</cpu>\n"
            + "<memory-gb>25</memory-gb>\n"
            + "<disk-size-gb>250</disk-size-gb>\n"
            + "</resources>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Resources resources = parseAndGetResources(xml);
    assertEquals(resources.getCpu(), 2.5);
    assertEquals(resources.getMemoryGb(), 25.0);
    assertEquals(resources.getDiskSizeGb(), 250);
  }

  public void testNetwork_NoSetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertTrue(network.isEmpty());
  }

  public void testNetwork_EmptySetting() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<network>\n"
            + "</network>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertTrue(network.isEmpty());
  }

  public void testNetwork_InstanceTag() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<network>\n"
            + "<instance-tag>mytag</instance-tag>\n"
            + "</network>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertEquals(network.getInstanceTag(), "mytag");
  }

  public void testNetwork_ForwardedPorts() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<network>\n"
            + "<forwarded-port>myport1</forwarded-port>"
            + "<forwarded-port>myport2</forwarded-port>"
            + "<forwarded-port>myport3:myport4</forwarded-port>"
            + "</network>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertEquals(network.getForwardedPorts().size(), 3);
    assertEquals(network.getForwardedPorts().get(0), "myport1");
    assertEquals(network.getForwardedPorts().get(1), "myport2");
    assertEquals(network.getForwardedPorts().get(2), "myport3:myport4");
  }

  public void testNetwork_Name() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<network>\n"
            + "<name>mynetwork</name>"
            + "</network>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertEquals(network.getName(), "mynetwork");
  }

  public void testNetwork_Full() {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<network>\n"
            + "<forwarded-port>myport1</forwarded-port>"
            + "<forwarded-port>myport2</forwarded-port>"
            + "<instance-tag>mytag</instance-tag>\n"
            + "<name>mynetwork</name>"
            + "</network>\n"
            + "</appengine-web-app>\n";
    AppEngineWebXml.Network network = parseAndGetNetwork(xml);
    assertEquals(network.getForwardedPorts().size(), 2);
    assertEquals(network.getForwardedPorts().get(0), "myport1");
    assertEquals(network.getForwardedPorts().get(1), "myport2");
    assertEquals(network.getName(), "mynetwork");
  }

  private void runConflictingScalingTest(String xml) throws Exception {
    String fullXml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + xml
            + "</appengine-web-app>\n";
    try {
      getAppEngineWebXml(fullXml);
      fail("AppEngineConfigException expected.");
    } catch (AppEngineConfigException e) {
      assertTrue(e.toString().contains("only one of 'automatic-scaling',"));
    }
  }

  public void testConflictingScaling_AutomaticBasic() throws Exception {
    String xml =
        "<basic-scaling>\n"
            + " <max-instances>11</max-instances>\n"
            + "</basic-scaling>\n"
            + "<automatic-scaling>\n"
            + " <max-idle-instances>11</max-idle-instances>\n"
            + "</automatic-scaling>\n";
    runConflictingScalingTest(xml);
  }

  public void testConflictingScaling_ManualAutomatic() throws Exception {
    String xml =
        "<manual-scaling>\n"
            + " <instances>11</instances>\n"
            + "</manual-scaling>\n"
            + "<automatic-scaling>\n"
            + " <max-idle-instances>11</max-idle-instances>\n"
            + "</automatic-scaling>\n";
    runConflictingScalingTest(xml);
  }

  public void testConflictingScaling_ManualBasic() throws Exception {
    String xml =
        "<manual-scaling>\n"
            + " <instances>11</instances>\n"
            + "</manual-scaling>\n"
            + "<basic-scaling>\n"
            + " <max-instances>11</max-instances>\n"
            + "</basic-scaling>\n";
    runConflictingScalingTest(xml);
  }

  private AppEngineWebXml parseUrlStreamHandlerType(String streamHandlerType) {
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>gianni-app</application>\n"
            + "<version>1</version>\n"
            + "<url-stream-handler>" + streamHandlerType + "</url-stream-handler>\n"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    return aewx;
  }

  public void testUrlStreamHandler() {
    assertEquals("native", parseUrlStreamHandlerType("native").getUrlStreamHandlerType());
    assertEquals("urlfetch", parseUrlStreamHandlerType("urlfetch").getUrlStreamHandlerType());
    assertTrue(parseUrlStreamHandlerType("native").toString()
        .contains("urlStreamHandlerType=native"));
    try {
      parseUrlStreamHandlerType("fail here");
      fail("Expected an AppEngineConfigException exception.");
    } catch (AppEngineConfigException e) {
      // pass
    }
  }

  public void testUseGoogleConnectorJ() {
    // Enabled explicitly
    String xml =
        "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
            + "<application>helloworld-java</application>\n"
            + "<version>1</version>\n"
            + "<use-google-connector-j>true</use-google-connector-j>"
            + "</appengine-web-app>\n";

    AppEngineWebXml aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    AppEngineWebXml expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setUseGoogleConnectorJ(true);
    assertEquals(expected, aewx);

    // Disabled explicitly
    xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
        + "<application>helloworld-java</application>\n"
        + "<version>1</version>\n"
        + "<use-google-connector-j>false</use-google-connector-j>"
        + "</appengine-web-app>\n";

    aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    expected.setUseGoogleConnectorJ(false);
    assertEquals(expected, aewx);

    // No set in the XML
    xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
        + "<application>helloworld-java</application>\n"
        + "<version>1</version>\n"
        + "</appengine-web-app>\n";

    aewx = getAppEngineWebXml(xml);
    assertNotNull(aewx);
    expected = newAppEngineWebXml();
    expected.setAppId("helloworld-java");
    expected.setMajorVersionId("1");
    assertEquals(expected, aewx);
  }
}

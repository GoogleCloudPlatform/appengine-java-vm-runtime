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

package com.google.apphosting.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test xml parsing.
 */
public class XmlTest {

    @Test
    public void testParse() throws Exception {
        String xml = "<appengine-web-app>" +
            "    <application>appName</application>" +
            "    <version>2</version>" +
            "    <threadsafe>true</threadsafe>" +
            "    <module>somemodule</module>" +
            "    <instance-class>B8</instance-class>" +
            "</appengine-web-app>";

        AppEngineWebXml appEngineWebXml = parse(xml);

        Assert.assertEquals("appName", appEngineWebXml.getAppId());
        Assert.assertEquals("2", appEngineWebXml.getMajorVersionId());
        Assert.assertTrue(appEngineWebXml.getThreadsafe());
        Assert.assertEquals("somemodule", appEngineWebXml.getModule());
        Assert.assertEquals("B8", appEngineWebXml.getInstanceClass());
    }

    @Test
    public void testManualScaling() throws Exception {
        String xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n" +
            "  <application>simple-app</application>\n" +
            "  <module>default</module>\n" +
            "  <version>uno</version>\n" +
            "  <threadsafe>true</threadsafe>" +
            "  <instance-class>B8</instance-class>\n" +
            "  <manual-scaling>\n" +
            "    <instances>5</instances>\n" +
            "  </manual-scaling>\n" +
            "</appengine-web-app>";
        AppEngineWebXml appEngineWebXml = parse(xml);

        AppEngineWebXml.ScalingType s = appEngineWebXml.getScalingType();
        Assert.assertNotNull(s);
        Assert.assertEquals(AppEngineWebXml.ScalingType.MANUAL, s);
    }

    @Test
    public void testBasicScaling() throws Exception {
        String xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n" +
            "  <application>simple-app</application>\n" +
            "  <module>default</module>\n" +
            "  <version>uno</version>\n" +
            "  <threadsafe>true</threadsafe>" +
            "  <instance-class>B8</instance-class>\n" +
            "  <basic-scaling>\n" +
            "    <max-instances>11</max-instances>\n" +
            "    <idle-timeout>10m</idle-timeout>\n" +
            "  </basic-scaling>\n" +
            "</appengine-web-app>";
        AppEngineWebXml appEngineWebXml = parse(xml);

        AppEngineWebXml.ScalingType s = appEngineWebXml.getScalingType();
        Assert.assertNotNull(s);
        Assert.assertEquals(AppEngineWebXml.ScalingType.BASIC, s);
    }

    @Test
    public void testAutoScaling() throws Exception {
        String xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n" +
            "  <application>simple-app</application>\n" +
            "  <module>default</module>\n" +
            "  <version>uno</version>\n" +
            "  <threadsafe>true</threadsafe>" +
            "  <instance-class>F2</instance-class>\n" +
            "  <automatic-scaling>\n" +
            "    <min-idle-instances>5</min-idle-instances>\n" +
            "    <max-idle-instances>automatic</max-idle-instances>\n" +
            "    <min-pending-latency>automatic</min-pending-latency>\n" +
            "    <max-pending-latency>30ms</max-pending-latency>\n" +
            "  </automatic-scaling>\n" +
            "</appengine-web-app>";
        AppEngineWebXml appEngineWebXml = parse(xml);

        AppEngineWebXml.ScalingType s = appEngineWebXml.getScalingType();
        Assert.assertNotNull(s);
        Assert.assertEquals(AppEngineWebXml.ScalingType.AUTOMATIC, s);
    }

    @Test
    public void testStaticFiles() throws Exception {
        String xml = "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n" +
            "    <application>capedwarf-test</application>\n" +
            "    <version>1</version>\n" +
            "    <threadsafe>true</threadsafe>\n" +
            "    <static-files>\n" +
            "        <include path=\"/**.png\" />\n" +
            "        <include path=\"/**.jpg\" expiration=\"1d 2h 3m 4s\"/>\n" +
            "        <exclude path=\"/**.jpg\" />\n" +
            "    </static-files>\n" +
            "</appengine-web-app>\n";
        AppEngineWebXml appEngineWebXml = parse(xml);

        List<AppEngineWebXml.StaticFileInclude> includes = appEngineWebXml.getStaticFileIncludes();
        Assert.assertEquals(2, includes.size());
        Assert.assertEquals("1d 2h 3m 4s", includes.get(1).getExpiration());
        List<String> excludes = appEngineWebXml.getStaticFileExcludes();
        // WEB-INF and jsp are excluded by default
        Assert.assertEquals(3, excludes.size());
    }

    @Test
    public void testSystemProperties() throws Exception {
        String xml = "<appengine-web-app>" +
            "    <application>appName</application>" +
            "    <version>2</version>" +
            "    <threadsafe>true</threadsafe>" +
            "    <system-properties>" +
            "        <property name=\"foo\" value=\"bar\"/>" +
            "    </system-properties>" +
            "</appengine-web-app>";

        AppEngineWebXml aewx = parse(xml);

        assertEquals("bar", aewx.getSystemProperties().get("foo"));
    }

    @Test
    public void testInboundServices() throws Exception {
        String xml = "<appengine-web-app>" +
            "    <application>appName</application>" +
            "    <version>2</version>" +
            "    <threadsafe>true</threadsafe>" +
            "    <inbound-services>" +
            "        <service>mail</service>" +
            "    </inbound-services>" +
            "</appengine-web-app>";

        AppEngineWebXml aewx = parse(xml);

        assertNotNull(aewx.getInboundServices());
        // "warmup" is added by default
        assertEquals(2, aewx.getInboundServices().size());
    }

    @Test
    public void testPublicRoot() throws Exception {
        String xml = "<appengine-web-app>" +
            "    <application>appName</application>" +
            "    <version>2</version>" +
            "    <threadsafe>true</threadsafe>" +
            "    <public-root>/static</public-root>" +
            "</appengine-web-app>";

        AppEngineWebXml aewx = parse(xml);
        assertEquals("/static", aewx.getPublicRoot());
    }

    @Test
    public void testAdminConsolePages() throws Exception {
        String xml = "<appengine-web-app>" +
            "    <application>appName</application>" +
            "    <version>2</version>" +
            "    <threadsafe>true</threadsafe>" +
            "    <admin-console>\n" +
            "        <page name=\"Custom Admin Page 1\" url=\"/admin/page1.html\"/>\n" +
            "        <page name=\"Custom Admin Page 2\" url=\"/admin/page2.html\"/>\n" +
            "    </admin-console>\n" +
            "</appengine-web-app>";

        AppEngineWebXml aewx = parse(xml);
        List<AppEngineWebXml.AdminConsolePage> pages = aewx.getAdminConsolePages();
        assertEquals(2, pages.size());
        AppEngineWebXml.AdminConsolePage page1 = pages.get(0);
        assertEquals("Custom Admin Page 1", page1.getName());
        assertEquals("/admin/page1.html", page1.getUrl());
        AppEngineWebXml.AdminConsolePage page2 = pages.get(1);
        assertEquals("Custom Admin Page 2", page2.getName());
        assertEquals("/admin/page2.html", page2.getUrl());
    }

    private static AppEngineWebXml parse(String xml) throws Exception {
        return new TestAppEngineWebXmlReader(new ByteArrayInputStream(xml.getBytes())).readAppEngineWebXml();
    }

    private static class TestAppEngineWebXmlReader extends AppEngineWebXmlReader {
        private final InputStream stream;

        public TestAppEngineWebXmlReader(InputStream stream) {
            super("");
            this.stream = stream;
        }

        @Override
        protected InputStream getInputStream() {
            return stream;
        }
    }
}

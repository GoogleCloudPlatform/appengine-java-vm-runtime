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

import com.google.apphosting.utils.config.AppEngineWebXml.ClassLoaderConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.PrioritySpecifierEntry;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Unit tests for {@link AppEngineWebXmlReader}.
 */
public class AppEngineWebXmlReaderTest extends TestCase {

  private static final String APP_ENGINE_WEB_XML_FORMAT =
      "src/test/resources/%s/%s_appengine-web.xml";

  private String getFilenameToParse(String testName) {
    String packagePath = getClass().getPackage().getName().replace('.', '/');
    return String.format(APP_ENGINE_WEB_XML_FORMAT, packagePath, testName);
  }

  public void testNonexistentFileThrowsAppEngineConfigException() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return "does not exist";
      }
    };

    try {
      reader.readAppEngineWebXml();
      fail("expected AppEngineConfigException");
    } catch (AppEngineConfigException e) {
      // good
    }
  }

  public void testExplosiveXmlProcessingThrowsAppEngineConfigException() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {

      @Override
      protected InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      protected AppEngineWebXml processXml(InputStream is) {
        throw new RuntimeException("ka-boom");
      }
    };
    try {
      reader.readAppEngineWebXml();
      fail("expected AppEngineConfigException");
    } catch (AppEngineConfigException e) {
      // good
      assertEquals("ka-boom", e.getCause().getMessage());
    }
  }

  public void testValid() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("Valid");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("myapp", aeWebXml.getAppId());
    assertEquals("1", aeWebXml.getMajorVersionId());
    assertTrue(aeWebXml.getSslEnabled());
    assertTrue(aeWebXml.getThreadsafeValueProvided());
  }

  public void testClassLoaderConfig() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("ClassLoaderConfig");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    ClassLoaderConfig config = aeWebXml.getClassLoaderConfig();
    assertNotNull(config);
    List<PrioritySpecifierEntry> entries = config.getEntries();
    assertNotNull(entries);
    assertEquals(1, entries.size());
    PrioritySpecifierEntry entry = entries.get(0);
    assertEquals("mailapi.jar", entry.getFilename());
    assertEquals(-1.0d, entry.getPriority());
  }

  public void testUrlStreamHandlerDefaultConfig() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("Minimal");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertNull(aeWebXml.getUrlStreamHandlerType());
  }

  public void testUrlStreamHandlerNativeConfig() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("UrlStreamHandler");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("native", aeWebXml.getUrlStreamHandlerType());
  }

  public void testEmptyAppId() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("Empty");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("", aeWebXml.getAppId());
    assertEquals("", aeWebXml.getMajorVersionId());
    assertFalse(aeWebXml.getSslEnabled());
  }

  public void testMinimal() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("Minimal");
      }
    };

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("myapp", aeWebXml.getAppId());
    assertEquals("1", aeWebXml.getMajorVersionId());
  }

  public void testMissingThreadsafeElement() {
    // First test the default behavior, which is that threadsafe is required.
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("NoThreadsafe");
      }
    };

    try {
      reader.readAppEngineWebXml();
    } catch (AppEngineConfigException aece) {
      assertTrue(aece.getMessage().startsWith(
          "appengine-web.xml does not contain a <threadsafe> element."));
    }

    // Now test customized behavior, where we allow a missing threadsafe element.
    reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("NoThreadsafe");
      }

      @Override
      protected boolean allowMissingThreadsafeElement() {
        return true;
      }
    };
    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("myapp", aeWebXml.getAppId());
    assertEquals("1", aeWebXml.getMajorVersionId());
    assertFalse(aeWebXml.getThreadsafeValueProvided());
  }

  public void testAutoIdPolicyWarning() {
    AppEngineWebXmlReader reader = new AppEngineWebXmlReader(".") {
      @Override
      public String getFilename() {
        return getFilenameToParse("AutoIdPolicy");
      }
    };
    class WarningDetector implements Filter {
      boolean warned = false;

      @Override public boolean isLoggable(LogRecord record) {
        warned = warned || record.getMessage().indexOf("Legacy auto ids are deprecated") >= 0;
        return true;
      }
    }

    WarningDetector warningDetector = new WarningDetector();
    Logger.getLogger(AppEngineWebXmlReader.class.getName()).setFilter(warningDetector);

    AppEngineWebXml aeWebXml = reader.readAppEngineWebXml();
    assertEquals("myapp", aeWebXml.getAppId());
    assertEquals("1", aeWebXml.getMajorVersionId());
    assertEquals("legacy", aeWebXml.getAutoIdPolicy());
    assertTrue("No deprecation warning logged for legacy auto ids", warningDetector.warned);
  }
}
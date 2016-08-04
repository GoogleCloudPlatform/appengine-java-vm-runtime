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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Unit Tests for {@link XmlUtils}.
 */
public class XmlUtilsTest extends TestCase {

  private static final String VALID_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n"
          + "<a>\n"
          + "  <b> B </b>\n"
          + "</a>\n";
  private static final String INVALID_XML = "<?xml version=\"1.0\" encoding=\"ut";

  public void testGetText_null() {
    Element node = mock(Element.class);
    when(node.getTextContent()).thenReturn(null);
    String result = XmlUtils.getText(node);
    assertEquals("", result);
  }

  public void testGetText() {
    Element node = mock(Element.class);
    when(node.getTextContent()).thenReturn("aa");
    String result = XmlUtils.getText(node);
    assertEquals("aa", result);
  }

  public void testGetText_trim() {
    Element node = mock(Element.class);
    when(node.getTextContent()).thenReturn("  a a  ");
    String result = XmlUtils.getText(node);
    assertEquals("a a", result);
  }

  public void testParse_ValidXml() throws UnsupportedEncodingException {
    InputStream is = new ByteArrayInputStream(VALID_XML.getBytes("ISO-8859-1"));
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    assertNotNull(root);
    assertEquals("a", root.getTagName());
    Element child = XmlUtils.getOptionalChildElement(root, "b");
    assertNotNull(child);
    assertEquals("B", XmlUtils.getText(child));
  }

  public void testParse_invalidXml() throws UnsupportedEncodingException {
    InputStream is = new ByteArrayInputStream(INVALID_XML.getBytes("ISO-8859-1"));
    try {
      XmlUtils.parseXml(is).getDocumentElement();
      fail();
    } catch (AppEngineConfigException e) {
      assertEquals("Received SAXException parsing the input stream.", e.getMessage());
    }
  }

  public void testParse_ioException() throws IOException {
    try {
      InputStream is = mock(InputStream.class);
      when(is.read()).thenThrow(new IOException("Expected Exception"));
      XmlUtils.parseXml(is).getDocumentElement();
      fail();
    } catch (AppEngineConfigException e) {
      assertEquals("Received IOException parsing the input stream.", e.getMessage());
    }
  }
}

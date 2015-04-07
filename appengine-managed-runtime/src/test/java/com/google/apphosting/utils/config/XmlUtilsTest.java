/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.Assert.*;

public class XmlUtilsTest {
    private static final String VALID_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n"
                    + "<a attr='value' empty=''>\n"
                    + "  <b> B </b>\n"
                    + "  <c> C </c>\n"
                    + "  <b> B2 </b>\n"
                    + "  <d><![CDATA[<value>]]></d>\n"
                    + "  <e>Hello <![CDATA[<value>]]></e>\n"
                    + "</a>\n";

    private static final String INVALID_XML = "<?xml version=\"1.0\" encoding=\"ut";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Document document;

    @Before
    public void parseXml() {
        InputStream is = new ByteArrayInputStream(VALID_XML.getBytes(ISO_8859_1));
        document = XmlUtils.parseXml(is);
    }

    @Test
    public void parsingValidXml() throws UnsupportedEncodingException {
        assertNotNull(document);
        Element root = document.getDocumentElement();
        assertEquals("a", root.getNodeName());
        Element b = (Element) root.getElementsByTagName("b").item(0);
        assertNotNull(b);
        assertEquals(" B ", b.getTextContent());
    }

    @Test
    public void invalidXmlCausesConfigException() throws UnsupportedEncodingException {
        InputStream is = new ByteArrayInputStream(INVALID_XML.getBytes(ISO_8859_1));

        thrown.expect(AppEngineConfigException.class);
        XmlUtils.parseXml(is);
    }

    @Test
    public void canLocateFirstChildElement() {
        Element parent = document.getDocumentElement();
        Element child = XmlUtils.getChildElement(parent, "b");
        assertNotNull(child);
        assertEquals(" B ", child.getTextContent());
    }

    @Test
    public void nullIsReturnedIfNoChildElement() {
        Element parent = document.getDocumentElement();
        Element child = XmlUtils.getChildElement(parent, "x");
        assertNull(child);
    }

    @Test
    public void canLocateMultipleChildElements() {
        Element parent = document.getDocumentElement();
        List<Element> children = XmlUtils.getChildren(parent, "b");
        assertNotNull(children);
        assertEquals(2, children.size());
        assertEquals(" B ", children.get(0).getTextContent());
        assertEquals(" B2 ", children.get(1).getTextContent());
    }

    @Test
    public void emptyListIsReturnedIfNoElementMatches() {
        Element parent = document.getDocumentElement();
        List<Element> children = XmlUtils.getChildren(parent, "x");
        assertNotNull(children);
        assertTrue(children.isEmpty());
    }

    @Test
    public void canRetrieveAttributeAsString() {
        Element a = document.getDocumentElement();
        assertEquals("value", XmlUtils.getAttribute(a, "attr"));
    }

    @Test
    public void emptyAttributeIsEmptyString() {
        Element a = document.getDocumentElement();
        assertEquals("", XmlUtils.getAttribute(a, "empty"));
    }

    @Test
    public void missingAttributeIsEmptyString() {
        Element a = document.getDocumentElement();
        assertEquals("", XmlUtils.getAttribute(a, "none"));
    }

    @Test
    public void canGetElementBodyText() {
        Element a = document.getDocumentElement();
        Element b = XmlUtils.getChildElement(a, "b");
        assertEquals(" B ", XmlUtils.getBody(b));
    }

    @Test
    public void canGetElementBodyTextWithCDATA() {
        Element a = document.getDocumentElement();
        Element d = XmlUtils.getChildElement(a, "d");
        assertEquals("<value>", XmlUtils.getBody(d));
    }

    @Test
    public void canGetElementBodyMixedTextWithCDATA() {
        Element a = document.getDocumentElement();
        Element e = XmlUtils.getChildElement(a, "e");
        assertEquals("Hello <value>", XmlUtils.getBody(e));
    }
}

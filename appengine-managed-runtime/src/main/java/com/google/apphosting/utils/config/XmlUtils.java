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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class XmlUtils {

    private XmlUtils() {
    }

    /**
     * Parses the input stream and returns the {@link Node} for the root element.
     *
     * @throws AppEngineConfigException If the input stream cannot be parsed.
     */
    static Document parseXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new AppEngineConfigException("Exception parsing the input stream.", e);
        }
    }

    /**
     * Returns the first child element with the given name.
     * @param parent the parent element
     * @param tagName the local name of the child to return
     * @return the first child element, or null if no element with that local name is present
     */
    static Element getChildElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    /**
     * Returns all direct child elements with the supplied name.
     * @param element    the parent element
     * @param tagName the local name of the children
     * @return a list of child elements, will not be null
     */
    static List<Element> getChildren(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);

        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    /**
     * Returns the value of an attribute.
     * @param element the element containing the attribute
     * @param name the name of the attribute
     * @return the value of the attribute, or empty string if the attribute is not present
     */
    static String getAttribute(Element element, String name) {
        return element.getAttribute(name);
    }

    /**
     * Returns the text in an element's body.
     * @param element the element
     * @return its body's text, may be null
     */
    static String getBody(Element element) {
        return element.getTextContent();
    }
}

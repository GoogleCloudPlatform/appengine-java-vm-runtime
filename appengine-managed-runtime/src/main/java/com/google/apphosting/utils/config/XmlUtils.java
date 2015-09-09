/**
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

import com.google.appengine.repackaged.com.google.common.base.Charsets;
import com.google.appengine.repackaged.com.google.common.io.Files;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

/**
 * Utility functions for processing XML.
 */
public class XmlUtils {

  /* Returns the trimmed text from the passed XmlParser.Node in node or an empty
   * if the passed in node does not contain any text.
   */
  static String getText(Element node) throws AppEngineConfigException {
    /*    Object child = node.get(0);
     String value;
     if (child == null) {
     value = "";
     } else {
     if (!(child instanceof String)) {
     String msg = "Invalid XML: String content expected in node '" + node.getTagName() + "'.";
     //  logger.log(Level.SEVERE, msg);
     throw new AppEngineConfigException(msg);
     }
     value = (String) child;
     }

     return value.trim();
     */
    String content = node.getTextContent();
    if (content == null) {
      return "";
    }
    return content.trim();
  }

  public static Document parseXml(InputStream inputStream) {
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(inputStream);
      doc.getDocumentElement().normalize();
      return doc;
    } catch (IOException e) {
      String msg = "Received IOException parsing the input stream.";
      throw new AppEngineConfigException(msg, e);
    } catch (SAXException e) {
      String msg = "Received SAXException parsing the input stream.";
      throw new AppEngineConfigException(msg, e);
    } catch (ParserConfigurationException e) {
      String msg = "Received ParserConfigurationException parsing the input stream.";
      throw new AppEngineConfigException(msg, e);
    }

  }

  /**
   * Validates a given XML document against a given schema.
   *
   * @param xmlFilename filename with XML document.
   * @param schema XSD schema to validate with.
   *
   * @throws AppEngineConfigException for malformed XML, or IO errors
   */
  public static void validateXml(String xmlFilename, File schema) {
    File xml = new File(xmlFilename);
    if (!xml.exists()) {
      throw new AppEngineConfigException("Xml file: " + xml.getPath() + " does not exist.");
    }
    if (!schema.exists()) {
      throw new AppEngineConfigException("Schema file: " + schema.getPath() + " does not exist.");
    }
    try {
      validateXmlContent(Files.toString(xml, Charsets.UTF_8), schema);
    } catch (IOException ex) {
      throw new AppEngineConfigException(
          "IO error validating " + xmlFilename + " against " + schema.getPath(), ex);
    }
  }

  /**
   * Validates a given XML document against a given schema.
   *
   * @param content a String containing the entire XML to validate.
   * @param schema XSD schema to validate with.
   *
   * @throws AppEngineConfigException for malformed XML, or IO errors
   */
  public static void validateXmlContent(String content, File schema) {
    if (!schema.exists()) {
      throw new AppEngineConfigException("Schema file: " + schema.getPath() + " does not exist.");
    }
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      try {
        factory
            .newSchema(schema)
            .newValidator()
            .validate(new StreamSource(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8))));
      } catch (SAXException ex) {
        throw new AppEngineConfigException(
            "XML error validating " + content + " against " + schema.getPath(), ex);
      }
    } catch (IOException ex) {
      throw new AppEngineConfigException(
          "IO error validating " + content + " against " + schema.getPath(), ex);
    }
  }

  public static String getChildElementBody(Element element, String tagName) {
    return getChildElementBody(element, tagName, true);
  }

  public static String getChildElementBody(Element element, String tagName, boolean required) {
    Element elt = getChildElement(element, tagName, required);
    return (elt != null) ? getBody(elt) : null;
  }

  public static Element getChildElement(Element parent, String tagName) {
    return getChildElement(parent, tagName, false);
  }

  public static Element getChildElement(Element parent, String tagName, boolean required) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes == null || nodes.getLength() == 0) {
      if (required) {
        throw new IllegalStateException(
            String.format("Missing tag %s in element %s.", tagName, parent));
      } else {
        return null;
      }
    }
    return (Element) nodes.item(0);
  }

  public static String getAttribute(Element element, String name) {
    return element.getAttribute(name);
  }

  public static String getBody(Element element) {
    NodeList nodes = element.getChildNodes();
    if (nodes == null || nodes.getLength() == 0) {
      return null;
    }

    Node firstNode = nodes.item(0);
    if (firstNode == null) {
      return null;
    }

    return firstNode.getNodeValue();
  }

  public static List<Element> getChildren(Element element, String tagName) {
    NodeList nodes = element.getElementsByTagName(tagName);

    List<Element> elements = new ArrayList<>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      elements.add((Element) nodes.item(i));
    }
    return elements;
  }
}

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

package com.google.apphosting.tests.usercode.testservlets;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.activation.DataHandler;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;

/**
 * Tests the SAAJ classes, i.e. the classes in the javax.xml.SOAP package.
 *
 */
public class SAAJTestServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/plain");
    try {
      testSAAJ();
    } catch (Exception e) {
      throw new ServletException("Unexpected exception in SAAJTestServlet", e);
    }
    response.getWriter().print("Success!");
  }

  private void testSAAJ() throws Exception {
    testSAAJ(SOAPConstants.SOAP_1_1_PROTOCOL);
    testSAAJ(SOAPConstants.SOAP_1_2_PROTOCOL);
  }

  /**
   * The main goal of this test is to exercise the SAAJ classes and make sure
   * that no exceptions are thrown. We build a SOAP message, we send it to a
   * mock SOAP server, which simply echoes the message back to us, and then we
   * check to make sure that the returned message is the same as the sent
   * message. We perform that check for equality in a very shallow way because
   * we are not seriously concerned about the possibility that the SOAP message
   * might be garbled. The check for equality should be thought of as only a
   * sanity check.
   */
  private void testSAAJ(String protocol) throws Exception {
    // Create the message
    MessageFactory factory = MessageFactory.newInstance(protocol);
    SOAPMessage requestMessage = factory.createMessage();

    // Add a header
    SOAPHeader header = requestMessage.getSOAPHeader();
    QName headerName = new QName("http://ws-i.org/schemas/conformanceClaim/", "Claim", "wsi");
    SOAPHeaderElement headerElement = header.addHeaderElement(headerName);
    headerElement.addAttribute(new QName("conformsTo"), "http://ws-i.org/profiles/basic/1.1/");

    // Add a body
    QName bodyName = new QName("http://wombat.ztrade.com", "GetLastTradePrice", "m");
    SOAPBody body = requestMessage.getSOAPBody();
    SOAPBodyElement bodyElement = body.addBodyElement(bodyName);
    QName name = new QName("symbol");
    SOAPElement symbol = bodyElement.addChildElement(name);
    symbol.addTextNode("SUNW");

    // Add an attachment
    AttachmentPart attachment = requestMessage.createAttachmentPart();
    String stringContent =
        "Update address for Sunny Skies " + "Inc., to 10 Upbeat Street, Pleasant Grove, CA 95439";
    attachment.setContent(stringContent, "text/plain");
    attachment.setContentId("update_address");
    requestMessage.addAttachmentPart(attachment);

    // Add another attachment
    URL url = new URL("http://greatproducts.com/gizmos/img.jpg");
    // URL url = new URL("file:///etc/passwords");
    DataHandler dataHandler = new DataHandler(url);
    AttachmentPart attachment2 = requestMessage.createAttachmentPart(dataHandler);
    attachment2.setContentId("attached_image");
    requestMessage.addAttachmentPart(attachment2);

    // Send the message
    SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
    SOAPConnection connection = soapConnectionFactory.createConnection();
    URL endpoint = new URL("http://wombat.ztrade.com/quotes");

    // Get the response. Our mock url-fetch handler will echo back the request
    SOAPMessage responseMessage = connection.call(requestMessage, endpoint);
    connection.close();

    assertEquals(requestMessage, responseMessage);
  }

  private static void assertEquals(SOAPMessage expected, SOAPMessage actual) throws Exception {
    assertAttachmentsAreEqual(expected, actual);
    assertSOAPHeadersAreEqual(expected, actual);
    assertSOAPBodiesAreEqual(expected, actual);
  }

  private static void assertAttachmentsAreEqual(SOAPMessage expected, SOAPMessage actual)
      throws Exception {
    int expectedNumAttachments = expected.countAttachments();
    int actualNumAttachments = actual.countAttachments();
    if (expectedNumAttachments != actualNumAttachments) {
      throw new Exception(
          "expectedNumAttachments="
              + expectedNumAttachments
              + "actualNumAttachments="
              + actualNumAttachments);
    }
    Iterator<?> expectedAttachmentIterator = expected.getAttachments();
    Iterator<?> actualAttachmentIterator = actual.getAttachments();
    for (int attachmentIndex = 0; attachmentIndex < actualNumAttachments; attachmentIndex++) {
      AttachmentPart expectedAttachment = (AttachmentPart) expectedAttachmentIterator.next();
      AttachmentPart actualAttachment = (AttachmentPart) actualAttachmentIterator.next();
      assertEquals(expectedAttachment, actualAttachment, attachmentIndex);
    }
  }

  private static void assertEquals(AttachmentPart expected, AttachmentPart actual, int index)
      throws Exception {
    // We'll just check that the IDs are equal
    String expectedId = expected.getContentId();
    String actualId = actual.getContentId();
    assertEquals(expectedId, actualId);
  }

  private static void assertSOAPHeadersAreEqual(SOAPMessage expected, SOAPMessage actual)
      throws Exception {
    SOAPHeader expectedHeader = expected.getSOAPHeader();
    SOAPHeaderElement expectedHeaderElement =
        (SOAPHeaderElement) expectedHeader.getChildElements().next();
    SOAPHeader actualHeader = actual.getSOAPHeader();
    SOAPHeaderElement actualHeaderElement =
        (SOAPHeaderElement) actualHeader.getChildElements().next();
    QName expectedHeaderName = expectedHeaderElement.getElementQName();
    String expectedQn = expectedHeaderName.toString();
    QName actualHeaderName = actualHeaderElement.getElementQName();
    String actualQn = actualHeaderName.toString();
    assertEquals(expectedQn, actualQn);
    // We'll just check that the first attributes are equal
    Iterator<?> expectedAttributes = expectedHeaderElement.getAllAttributes();
    Iterator<?> actualAttributes = actualHeaderElement.getAllAttributes();
    Name expectedAttributeName;
    try {
      expectedAttributeName = (Name) expectedAttributes.next();
    } catch (NoSuchElementException e) {
      throw new Exception("expectedHeader " + expectedQn + " does not have any attributes");
    }
    Name actualAttributeName;
    try {
      actualAttributeName = (Name) actualAttributes.next();
    } catch (NoSuchElementException e) {
      throw new Exception("actualHeader " + actualQn + " does not have any attributes");
    }
    expectedQn = expectedAttributeName.getQualifiedName();
    actualQn = actualAttributeName.getQualifiedName();
    assertEquals(expectedQn, actualQn);
  }

  private static void assertSOAPBodiesAreEqual(SOAPMessage expected, SOAPMessage actual)
      throws Exception {
    SOAPBody expectedBody = expected.getSOAPBody();
    SOAPBody actualBody = actual.getSOAPBody();
    SOAPBodyElement expectedElement = (SOAPBodyElement) expectedBody.getChildElements().next();
    SOAPBodyElement actualElement = (SOAPBodyElement) actualBody.getChildElements().next();
    String expectedQn = expectedElement.getElementQName().toString();
    String actualQn = actualElement.getElementQName().toString();
    assertEquals(expectedQn, actualQn);
  }

  private static void assertEquals(String expected, String actual) throws Exception {
    if (!expected.equals(actual)) {
      throw new Exception("expected=" + expected + " actual=" + actual);
    }
  }
}

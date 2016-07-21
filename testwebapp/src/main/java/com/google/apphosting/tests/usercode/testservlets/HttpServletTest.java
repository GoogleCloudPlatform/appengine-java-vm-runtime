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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

/**
 * Base class with useful asserts that send output to an {@link HttpServletResponse}.
 *
 *
 */
class HttpServletTest extends HttpServlet {

  static class AssertionFailedException extends Exception {
    public AssertionFailedException(String message) {
      super(message);
    }
  }

  static void assertNull(String name, Object obj, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(1, name + " is not null", null == obj, response);
  }

  static void assertNotNull(String name, Object obj, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(1, name + " is null", null != obj, response);
  }

  static void assertEquals(String message, int a, int b, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(1, message + " expected: '" + a + "' but was: '" + b + "'", a == b, response);
  }

  static void assertEquals(String message, Object a, Object b, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(
        1,
        message + " expected: '" + a + "' but was: '" + b + "'",
        (a == null ? b == null : a.equals(b)),
        response);
  }

  static void assertTrue(String message, boolean condition, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(1, message, condition, response);
  }

  static void assertFalse(String message, boolean condition, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    assertTrue(1, message, !condition, response);
  }

  static void assertTrue(
      int callStackOffset, String message, boolean condition, HttpServletResponse response)
      throws AssertionFailedException, IOException {
    if (!condition) {
      AssertionFailedException exception = new AssertionFailedException(message);
      StackTraceElement[] stackTrace = exception.getStackTrace();
      StackTraceElement element = stackTrace[1 + callStackOffset];
      String fileName = element.getFileName();
      int lineNumber = element.getLineNumber();
      response.getWriter().print(fileName + ":" + lineNumber + " Assertion Failed: " + message);
      throw exception;
    }
  }
}

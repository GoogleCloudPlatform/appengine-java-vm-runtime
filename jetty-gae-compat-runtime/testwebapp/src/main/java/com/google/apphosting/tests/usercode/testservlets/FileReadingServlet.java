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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Demonstrates use of {@link File}, {@link FileInputStream}, and
 * {@link FileReader}.
 *
 */
public class FileReadingServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    boolean useFileStream = Boolean.valueOf(request.getParameter("useFileInputStream"));
    String fileReferenceType = request.getParameter("fileReferenceType");
    boolean existsOnly = request.getParameter("existsOnly") != null;

    File file;
    if ("realpath".equals(fileReferenceType)) {
      file = new File(getServletContext().getRealPath("index.html"));
    } else if ("explicitpath".equals(fileReferenceType)) {
      file = new File(System.getProperty("user.dir"), "index.html");
    } else if ("emptyroot".equals(fileReferenceType)) {
      file = new File(new File(""), "index.html");
    } else if ("abspath".equals(fileReferenceType)) {
      file = new File("index.html").getAbsoluteFile();
    } else if ("relpath".equals(fileReferenceType)) {
      file = new File("index.html");
    } else if ("newline".equals(fileReferenceType)) {
      file = new File("index.html\nSome other string.");
    } else if ("param".equals(fileReferenceType)) {
      file = new File(request.getParameter("filename"));
    } else if ("paramrealpath".equals(fileReferenceType)) {
      file = new File(getServletContext().getRealPath(request.getParameter("filename")));
    } else {
      throw new IllegalArgumentException("Unexpected fileReferenceType: " + fileReferenceType);
    }

    if (existsOnly) {
      response.getWriter().print(String.valueOf(file.exists()));
    } else if (useFileStream) {
      // read the contents of the file using a FileInputStream
      FileInputStream fis = new FileInputStream(file);
      byte[] buffer = new byte[8192];
      int len;
      while ((len = fis.read(buffer, 0, buffer.length)) != -1) {
        response.getOutputStream().write(buffer, 0, len);
      }
    } else {
      char[] charBuffer = new char[8192];
      int len;
      // read the contents of the file using a FileReader
      FileReader fr = new FileReader(file);
      while ((len = fr.read(charBuffer, 0, charBuffer.length)) != -1) {
        response.getWriter().print(Arrays.copyOf(charBuffer, len));
      }
    }
  }
}

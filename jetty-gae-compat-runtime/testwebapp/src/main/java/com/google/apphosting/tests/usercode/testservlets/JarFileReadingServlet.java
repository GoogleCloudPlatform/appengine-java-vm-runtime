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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Demonstrates use of {@link JarFile}.
 */
public class JarFileReadingServlet extends HttpServlet {

  private static final String JAR_FILE_PREFIX = "jar:file:";

  private String getPathOfJarContainingThisClass() {
    // We figure out the absolute path of the jar that contains
    // this class by looking up the classfile as a resource and then
    // extracting all text in between JAR_FILE_PREFIX and !.
    // Hacky, but this is what Datanucleus does so it's a fair
    // use case.
    URL xml = getClass().getResource(getClass().getSimpleName() + ".class");
    String path = xml.toExternalForm();
    int index = path.indexOf('!');
    return path.substring(JAR_FILE_PREFIX.length(), index);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    File jarFile = new File(getPathOfJarContainingThisClass());
    File jarDirectory = jarFile.getParentFile();
    // write the contents of the directory to the response
    for (File f : jarDirectory.listFiles()) {
      response.getOutputStream().print(f.getName());
    }
    JarFile jar = new JarFile(jarFile);
    JarEntry entry =
        jar.getJarEntry(getClass().getPackage().getName().replace(".", "/") + "/AnXmlResource.xml");
    InputStream is = jar.getInputStream(entry);
    byte[] buffer = new byte[8192];
    int len;
    while ((len = is.read(buffer, 0, buffer.length)) != -1) {
      response.getOutputStream().write(buffer, 0, len);
    }
  }
}

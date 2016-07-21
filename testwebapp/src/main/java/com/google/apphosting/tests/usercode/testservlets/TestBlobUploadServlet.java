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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.UploadOptions;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestBlobUploadServlet extends HttpServlet {
  private BlobstoreService blobstoreService;

  static final String UPLOAD_HEADER = "X-AppEngine-BlobUpload";

  @Override
  public void init() {
    this.blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String target = req.getParameter("target");
    if (target == null) {
      target = req.getRequestURI();
    }

    UploadOptions options = UploadOptions.Builder.withDefaults();
    String maxUploadSizeString = req.getParameter("max-upload-size");
    if (maxUploadSizeString != null) {
      try {
        options.maxUploadSizeBytes(Long.valueOf(maxUploadSizeString));
      } catch (NumberFormatException ex) {
        // ignored
      }
    }

    String maxPerBlobSize = req.getParameter("max-per-blob-size");
    if (maxPerBlobSize != null) {
      try {
        options.maxUploadSizeBytesPerBlob(Long.valueOf(maxPerBlobSize));
      } catch (NumberFormatException ex) {
        // ignored
      }
    }

    String gsBucketName = req.getParameter("gs-bucket-name");
    if (gsBucketName != null) {
      options.googleStorageBucketName(gsBucketName);
    }

    String url = blobstoreService.createUploadUrl(target, options);

    resp.setContentType("text/html");
    PrintWriter out = resp.getWriter();
    if (req.getParameter("urlOnly") != null) {
      out.print(url);
    } else {
      out.println("<html><body>");
      out.println(
          "<form id=form1 method=\"POST\" action=\"" + url + "\" enctype=\"multipart/form-data\">");
      out.println("<input id=text1 type=\"text\" name=\"text1\">");
      out.println("<input id=file1 type=\"file\" name=\"file1\">");
      out.println("<input id=file2 type=\"file\" name=\"file2\">");
      out.println("<input id=submit1 type=\"submit\" value=\"Upload\">");
      out.println("</body></html>");
    }
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Map<String, BlobKey> blobs = blobstoreService.getUploadedBlobs(req);

    // Verify all of the header accessors contain the upload header.
    if (!req.getHeader(UPLOAD_HEADER).equals("true")
        || !Collections.list(req.getHeaderNames()).contains(UPLOAD_HEADER)
        || !Collections.list(req.getHeaders(UPLOAD_HEADER)).contains("true")) {
      resp.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Expected header to be present: " + UPLOAD_HEADER);
      return;
    }
    try {
      req.getIntHeader(UPLOAD_HEADER);
      resp.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Expected header to be present: " + UPLOAD_HEADER);
      return;
    } catch (NumberFormatException expected) {
    }

    Map<String, String[]> parameterMap = req.getParameterMap();
    if (!parameterMap.containsKey("text1")) {
      resp.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Expected text1 to be found in the parameterMap.");
    }
    if (req.getParameter("text1") == null || req.getParameter("text1").length() == 0) {
      resp.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Expected non-empty text1 field.");
      return;
    }

    // Verify blob keys are present.
    if (blobs.size() == 1) {
      BlobKey blobKey = blobs.entrySet().iterator().next().getValue();
      resp.sendRedirect("/serve-blob?key=" + blobKey.getKeyString());
    } else {
      resp.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Expect one uploaded blob, got " + blobs.size());
    }
  }
}

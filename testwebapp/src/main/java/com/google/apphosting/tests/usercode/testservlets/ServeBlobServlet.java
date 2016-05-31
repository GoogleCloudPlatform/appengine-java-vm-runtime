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
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServeBlobServlet extends HttpServlet {
  @Override
  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    BlobKey blobKey = new BlobKey(req.getParameter("key"));
    String contentType = req.getParameter("content-type");
    String blobRange = req.getParameter("blob-range");

    if (blobRange != null) {
      resp.setHeader("X-AppEngine-BlobRange", blobRange);
    }

    if (req.getParameter("delete") != null) {
      BlobstoreServiceFactory.getBlobstoreService().delete(blobKey);
    }

    if (req.getParameter("commitBefore") != null) {
      resp.getWriter().println("Committing before.");
      resp.getWriter().flush();
    }

    BlobstoreServiceFactory.getBlobstoreService().serve(blobKey, resp);
    if (contentType != null) {
      resp.setContentType(contentType);
    }

    if (req.getParameter("commitAfter") != null) {
      resp.getWriter().println("Committing after.");
      resp.getWriter().flush();
    }
  }
}

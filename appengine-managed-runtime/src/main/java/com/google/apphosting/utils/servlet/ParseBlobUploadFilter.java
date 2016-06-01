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

package com.google.apphosting.utils.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * {@code ParseBlobUploadFilter} is responsible for the parsing
 * multipart/form-data or multipart/mixed requests used to make Blob
 * upload callbacks, and storing a set of string-encoded blob keys as
 * a servlet request attribute.  This allows the {@code
 * BlobstoreService.getUploadedBlobs()} method to return the
 * appropriate {@code BlobKey} objects.
 *
 * <p>This filter automatically runs on all dynamic requests in the
 * production environment.  In the DevAppServer, the equivalent work
 * is subsumed by {@code UploadBlobServlet}.
 *
 */
public class ParseBlobUploadFilter implements Filter {
  private static final Logger logger = Logger.getLogger(ParseBlobUploadFilter.class.getName());

  /**
   * An arbitrary HTTP header that is set on all blob upload
   * callbacks.
   */
  static final String UPLOAD_HEADER = "X-AppEngine-BlobUpload";

  static final String UPLOADED_BLOBKEY_ATTR = "com.google.appengine.api.blobstore.upload.blobkeys";

  static final String UPLOADED_BLOBINFO_ATTR =
      "com.google.appengine.api.blobstore.upload.blobinfos";

  // This field has to be the same as X_APPENGINE_CLOUD_STORAGE_OBJECT in http_proto.cc.
  // This header will have the creation date in the format YYYY-MM-DD HH:mm:ss.SSS.
  static final String UPLOAD_CREATION_HEADER = "X-AppEngine-Upload-Creation";

  // This field has to be the same as X_APPENGINE_CLOUD_STORAGE_OBJECT in http_proto.cc.
  // This header will have the filename of created the object in Cloud Storage when appropriate.
  static final String CLOUD_STORAGE_OBJECT_HEADER = "X-AppEngine-Cloud-Storage-Object";

  static final String CONTENT_LENGTH_HEADER = "Content-Length";

  public void init(FilterConfig config) {}

  public void destroy() {}

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    if (req.getHeader(UPLOAD_HEADER) != null) {
      Map<String, List<String>> blobKeys = new HashMap<String, List<String>>();
      Map<String, List<Map<String, String>>> blobInfos =
          new HashMap<String, List<Map<String, String>>>();
      Map<String, List<String>> otherParams = new HashMap<String, List<String>>();

      try {
        MimeMultipart multipart = MultipartMimeUtils.parseMultipartRequest(req);

        int parts = multipart.getCount();
        for (int i = 0; i < parts; i++) {
          BodyPart part = multipart.getBodyPart(i);
          String fieldName = MultipartMimeUtils.getFieldName(part);
          if (part.getFileName() != null) {
            ContentType contentType = new ContentType(part.getContentType());
            if ("message/external-body".equals(contentType.getBaseType())) {
              String blobKeyString = contentType.getParameter("blob-key");
              List<String> keys = blobKeys.get(fieldName);
              if (keys == null) {
                keys = new ArrayList<String>();
                blobKeys.put(fieldName, keys);
              }
              keys.add(blobKeyString);
              List<Map<String, String>> infos = blobInfos.get(fieldName);
              if (infos == null) {
                infos = new ArrayList<Map<String, String>>();
                blobInfos.put(fieldName, infos);
              }
              infos.add(getInfoFromBody(MultipartMimeUtils.getTextContent(part), blobKeyString));
            }
          } else {
            List<String> values = otherParams.get(fieldName);
            if (values == null) {
              values = new ArrayList<String>();
              otherParams.put(fieldName, values);
            }
            values.add(MultipartMimeUtils.getTextContent(part));
          }
        }
        req.setAttribute(UPLOADED_BLOBKEY_ATTR, blobKeys);
        req.setAttribute(UPLOADED_BLOBINFO_ATTR, blobInfos);
      } catch (MessagingException ex) {
        logger.log(Level.WARNING, "Could not parse multipart message:", ex);
      }

      chain.doFilter(new ParameterServletWrapper(request, otherParams), response);
    } else {
      chain.doFilter(request, response);
    }
  }

  private Map<String, String> getInfoFromBody(String bodyContent, String key)
      throws MessagingException {
    MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(bodyContent.getBytes()));
    Map<String, String> info = new HashMap<String, String>(6);
    info.put("key", key);
    info.put("content-type", part.getContentType());
    info.put("creation-date", part.getHeader(UPLOAD_CREATION_HEADER)[0]);
    info.put("filename", part.getFileName());
    info.put("size", part.getHeader(CONTENT_LENGTH_HEADER)[0]); // part.getSize() returns 0
    info.put("md5-hash", part.getContentMD5());

    String[] headers = part.getHeader(CLOUD_STORAGE_OBJECT_HEADER);
    if (headers != null && headers.length == 1) {
      info.put("gs-name", headers[0]);
    }

    return info;
  }

  private static class ParameterServletWrapper extends HttpServletRequestWrapper {
    private final Map<String, List<String>> otherParams;

    ParameterServletWrapper(ServletRequest request, Map<String, List<String>> otherParams) {
      super((HttpServletRequest) request);
      this.otherParams = otherParams;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Map getParameterMap() {
      @SuppressWarnings("unchecked")
      Map<String, String[]> parameters = super.getParameterMap();
      if (otherParams.isEmpty()) {
        return parameters;
      } else {
        // HttpServlet.getParameterMap() result is immutable so we need to take a copy.
        Map<String, String[]> map = new HashMap<String, String[]>(parameters);
        for (Map.Entry<String, List<String>> entry : otherParams.entrySet()) {
          map.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        // Maintain the semantic of ServletRequstWrapper by returning
        // an immutable map.
        return Collections.unmodifiableMap(map);
      }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getParameterNames() {
      List<String> allNames = new ArrayList<String>();

      @SuppressWarnings("unchecked")
      Enumeration<String> names = super.getParameterNames();
      while (names.hasMoreElements()) {
        allNames.add(names.nextElement());
      }
      allNames.addAll(otherParams.keySet());
      return Collections.enumeration(allNames);
    }

    @Override
    public String[] getParameterValues(String name) {
      if (otherParams.containsKey(name)) {
        return otherParams.get(name).toArray(new String[0]);
      } else {
        return super.getParameterValues(name);
      }
    }

    @Override
    public String getParameter(String name) {
      if (otherParams.containsKey(name)) {
        return otherParams.get(name).get(0);
      } else {
        return super.getParameter(name);
      }
    }
  }
}

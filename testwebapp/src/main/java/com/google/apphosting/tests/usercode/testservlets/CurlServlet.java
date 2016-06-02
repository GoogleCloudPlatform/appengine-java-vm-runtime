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

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CurlServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    String url = req.getParameter("url");
    String deadlineSecs = req.getParameter("deadline");
    URLFetchService service = URLFetchServiceFactory.getURLFetchService();
    HTTPRequest fetchReq = new HTTPRequest(new URL(url));
    if (deadlineSecs != null) {
      fetchReq.getFetchOptions().setDeadline(Double.valueOf(deadlineSecs));
    }
    HTTPResponse fetchRes = service.fetch(fetchReq);
    for (HTTPHeader header : fetchRes.getHeaders()) {
      res.addHeader(header.getName(), header.getValue());
    }
    if (fetchRes.getResponseCode() == 200) {
      res.getOutputStream().write(fetchRes.getContent());
    } else {
      res.sendError(fetchRes.getResponseCode(), "Error while fetching");
    }
  }
}

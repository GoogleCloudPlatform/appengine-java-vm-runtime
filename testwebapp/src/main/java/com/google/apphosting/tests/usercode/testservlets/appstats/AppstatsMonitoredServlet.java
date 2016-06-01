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

package com.google.apphosting.tests.usercode.testservlets.appstats;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple servlet that performs the following operations:
 *
 * <ul>
 *   <li>Store a random value into memcache and print the key it accessed in the response.</li>
 *   <li>Do an asynchronous urlfetch and wait for the response.</li>
 *   <li>Do an asynchronous urlfetch and do not wait for the response.</li>
 * </ul>
 *
 * The servlet is monitored by app stats,
 * which should make it possible for selenium tests to check that all the
 * operations were actually recorded.
 *
 */
public class AppstatsMonitoredServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      URLFetchServiceFactory.getURLFetchService()
          .fetchAsync(new URL("http://www.example.com/asyncWithGet"))
          .get();
    } catch (Exception anythingMightGoWrongHere) {
      // fall through
    }
    URLFetchServiceFactory.getURLFetchService()
        .fetchAsync(new URL("http://www.example.com/asyncWithoutGet"));
    MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
    String randomKey = "" + Math.random();
    String randomValue = "" + Math.random();
    memcache.put(randomKey, randomValue);
    resp.setContentType("text/plain");
    resp.getOutputStream().println(randomKey);
  }
}

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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that adds data required by our data viewer selenium tests.
 * Deploying a userland servlet to do this is cleaner than trying to
 * set the data up in the test itself because we need all of this code
 * to execute under the IsolatedClassLoader.
 *
 */
public class DataViewerTestDataServlet extends HttpServlet {

  public static final String SAMPLE_KIND_1 = "'sample' \"kind\" <1>";

  public static final String SAMPLE_KIND_2 = "sample kind 2";

  public static final String PAGINATION_KIND = "pagination";

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

    if (httpServletRequest.getParameter("returnNumEntities") != null) {
      httpServletResponse
          .getWriter()
          .print(ds.prepare(new Query()).countEntities(FetchOptions.Builder.withDefaults()));
    } else {
      // add our dummy data to the datastore;
      Entity entity = new Entity(SAMPLE_KIND_1, "the name 1");
      entity.setUnindexedProperty("p1", "v1");
      entity.setProperty("p2", "v2");
      entity.setProperty("p3", "v3");
      entity.setUnindexedProperty("p4", "v4");
      entity.setUnindexedProperty("p5", "v5");
      ds.put(entity);

      Entity e2 = new Entity(SAMPLE_KIND_1, "the name 2");
      e2.setUnindexedProperty("p0", "v7");
      e2.setProperty("p1", "v4");
      e2.setUnindexedProperty("p3", "v5");
      ds.put(e2);

      Entity e3 = new Entity(SAMPLE_KIND_2, "the name");
      ds.put(e3);

      String nameFormat = "the name %04d";
      for (int i = 0; i < 1025; i++) {
        entity = new Entity(PAGINATION_KIND, String.format(nameFormat, i));
        entity.setProperty("p1", "v1");
        entity.setProperty("num", i);
        ds.put(entity);
      }

      NamespaceManager.set("other");
      Entity e4 = new Entity(SAMPLE_KIND_1, "different namespace");
      e4.setProperty("prop1", "val1");
      e4.setProperty("prop2", "val2");
      ds.put(e4);

      for (int i = 0; i < 1020; i++) {
        entity = new Entity(PAGINATION_KIND, String.format(nameFormat, i));
        entity.setProperty("new namespace p1", "new namespace v1");
        entity.setProperty("new namespace num", i);
        ds.put(entity);
      }
    }
  }
}

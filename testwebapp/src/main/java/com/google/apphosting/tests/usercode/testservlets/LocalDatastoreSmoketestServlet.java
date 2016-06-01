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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that puts the local datastore through its paces.
 *
 */
public class LocalDatastoreSmoketestServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity("foo");
    entity.setProperty("foo", 23);
    Key key = ds.put(entity);

    try {
      entity = ds.get(key);
    } catch (EntityNotFoundException e1) {
      throw new ServletException(e1);
    }

    entity.setProperty("bar", 44);
    ds.put(entity);

    Query query = new Query("foo");
    query.addFilter("foo", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 22);
    Iterator<Entity> iter = ds.prepare(query).asIterator();
    iter.next();
  }
}

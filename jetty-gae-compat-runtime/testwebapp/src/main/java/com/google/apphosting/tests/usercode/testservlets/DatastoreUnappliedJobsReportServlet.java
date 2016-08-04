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
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that kicks off some datastore jobs and reports which jobs fail
 * to immediately apply, as a way to test the consistency model.
 */
public class DatastoreUnappliedJobsReportServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    PrintWriter writer = resp.getWriter();
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // By default ~10% fail to apply.
    Key[] keys = new Key[50];
    for (int i = 0; i < keys.length; ++i) {
      keys[i] = datastore.put(new Entity("foo"));
    }

    // Perform eventually consistent query and report unapplied jobs.
    QueryResultList<Entity> results =
        datastore
            .prepare(new Query("foo").setKeysOnly())
            .asQueryResultList(FetchOptions.Builder.withDefaults());
    writer.println("Of " + keys.length + " jobs " + results.size() + " applied");
    writer.print("Query results: ");
    Set<Key> applied = new HashSet<Key>();
    for (Entity entity : results) {
      applied.add(entity.getKey());
    }
    for (Key key : keys) {
      writer.append(applied.contains(key) ? '1' : '0');
    }
    writer.append('\n');

    // Fetch test entities directly to confirm written.
    for (Key key : keys) {
      try {
        datastore.get(key);
      } catch (EntityNotFoundException e) {
        writer.println("Error: missing entity " + key.toString());
      }
    }
  }
}

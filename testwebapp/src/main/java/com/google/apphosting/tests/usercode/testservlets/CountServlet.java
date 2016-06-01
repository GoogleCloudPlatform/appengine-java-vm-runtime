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
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Simple stateful servlet that returns the number of times it has been called. It can either
 * store the count in local state or store it in memcache, datastore, or session store.
 *
 *
 */
public class CountServlet extends HttpServlet {
  private static final String KEY = "count";

  static AtomicInteger localCount = new AtomicInteger(0);
  private static AtomicInteger privateLocalCount = new AtomicInteger(0);

  private int datastoreCount() {
    Key key = KeyFactory.createKey(Long.class.getName(), KEY);
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    Entity entity;
    long value = -1;
    try {
      entity = ds.get(key);
      Long old = ((Long) entity.getProperty(KEY));
      value = old.longValue() + 1;
      entity.setProperty(KEY, Long.valueOf(value));
      ds.put(entity);
    } catch (Exception e) {
      // not found, insert
      entity = new Entity(key);
      value = 1;
      entity.setProperty(KEY, Long.valueOf(value));
      ds.put(entity);
    }
    tx.commit();
    return (int) value;
  }

  private int memcacheCount() {
    int memcacheValue =
        (int)
            MemcacheServiceFactory.getMemcacheService()
                .increment(KEY, Long.valueOf(1), Long.valueOf(0))
                .longValue();
    return memcacheValue;
  }

  private long sessionStoreCount(HttpServletRequest req) {
    try {
      HttpSession session = req.getSession(true);
      Object value = session.getAttribute(KEY);
      long count = 1;
      if (value != null && value instanceof Long) {
        count = ((Long) value) + 1;
      }
      session.setAttribute(KEY, Long.valueOf(count));
      return count;
    } catch (RuntimeException e) {
      // If sessions are disabled we expect this specific error message. Return -1 to the caller.
      String msg = e.getMessage();
      if (msg != null && msg.startsWith("Session support is not enabled in appengine-web.xml")) {
        return -1;
      }
      // Some other error, throw it.
      throw e;
    }
  }

  /**
   * returns the number of times it is been called as a string. Takes an
   * optional "type" argument that can be either memcache or datastore. If type
   * is specified the state is counted from the specified storage service, if no
   * type is specified the local process state is used for storage
   */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String type = req.getParameter("type");
    if ("datastore".equals(type)) {
      res.getWriter().println("" + datastoreCount());
    } else if ("memcache".equals(type)) {
      res.getWriter().println("" + memcacheCount());
    } else if ("private-local".equals(type)) {
      int count = privateLocalCount.incrementAndGet();
      res.getWriter().println("" + count);
    } else if (type == null || "local".equals(type)) {
      // default, just use the in process state
      int count = localCount.incrementAndGet();
      res.getWriter().println("" + count);
    } else if ("session".equals(type)) {
      res.getWriter().println("" + sessionStoreCount(req));
    } else {
      res.getWriter().println("unknown storage type: " + type);
    }
  }
}

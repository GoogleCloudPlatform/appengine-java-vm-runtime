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

import com.google.appengine.api.LifecycleManager;
import com.google.appengine.api.LifecycleManager.ShutdownHook;
import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Used for testing requests to /_ah/start on server startup. Will read a value
 * from the datastore and assign it to CountServlet.count
 *
 * Will also install a shutdown hook that stores the count value to the
 * datastore on server shutdown
 */
public class ServersStartServlet extends HttpServlet {
  private BackendService backendService;

  private int datastoreCount() {
    Key key = KeyFactory.createKey("Counter", getKeyName());
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity;
    try {
      entity = ds.get(key);
      Long val = ((Long) entity.getProperty("value"));
      return val.intValue();
    } catch (Exception e) {
      // not found
      return 0;
    }
  }

  @Override
  public void init() {
    backendService = BackendServiceFactory.getBackendService();
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    CountServlet.localCount.getAndAdd(datastoreCount());
    final Key key = KeyFactory.createKey("Counter", getKeyName());
    LifecycleManager.getInstance()
        .setShutdownHook(
            new ShutdownHook() {
              @Override
              public void shutdown() {
                datastoreSave(key);
              }
            });
  }

  private void datastoreSave(Key key) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    Entity entity = new Entity(key);
    entity.setProperty("value", Integer.valueOf(CountServlet.localCount.get()));
    ds.put(entity);
    tx.commit();
  }

  private String getKeyName() {
    return backendService.getCurrentBackend() + ":" + backendService.getCurrentInstance();
  }
}

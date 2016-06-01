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

package com.google.apphosting.tests.usercode.testservlets.remoteapi;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;

import java.io.IOException;

/**
 * App Engine client example code
 * illustrating how to use Remote API from within an App Engine app.  If this
 * code is not compiling you must update the documentation with any changes you
 * make to this file.
 *
 */
class RemoteApiInsideAppEngineExample {
  private final RemoteApiOptions options;

  RemoteApiInsideAppEngineExample(String username, String password) throws IOException {
    // Authenticating with username and password is slow, so we'll do it
    // once during construction and then store the credentials for reuse.
    this.options =
        new RemoteApiOptions()
            .server("&ltyour target app&gt;.appspot.com", 443)
            .credentials(username, password);
    RemoteApiInstaller installer = new RemoteApiInstaller();
    installer.install(options);
    try {
      // Update the options with reusable credentials so we can skip
      // authentication on subsequent calls.
      options.reuseCredentials(username, installer.serializeCredentials());
    } finally {
      installer.uninstall();
    }
  }

  void putInRemoteDatastore(Entity entity) throws IOException {
    RemoteApiInstaller installer = new RemoteApiInstaller();
    installer.install(options);
    try {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      System.out.println("Key of new entity is " + ds.put(entity));
    } finally {
      installer.uninstall();
    }
  }
}

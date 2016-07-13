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

package com.google.apphosting.vmruntime.jetty9;

import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;

public class AppengineApiConfiguration extends AbstractConfiguration {

  // A class to check if the GAE API is available.
  public static final String GAE_CHECK_CLASS = "com.google.appengine.api.ThreadManager";

  // It's undesirable to have the user app override classes provided by us.
  // So we mark them as Jetty system classes, which cannot be overridden.
  private static final String[] SYSTEM_CLASSES = {
    "com.google.appengine.api.",
    "com.google.appengine.tools.",
    "com.google.apphosting.",
    "com.google.cloud.sql.jdbc.",
    "com.google.protos.cloud.sql.",
    "com.google.storage.onestore.",
  };
  
  // Hide the container classes from the webapplication
  private static final String[] SERVER_CLASSES = {
    "org.apache.commons.codec.", 
    "org.apache.commons.logging.",
    "org.apache.http.",
    "com.google.gson."
  };

  @Override
  public void preConfigure(WebAppContext context) {
    for (String systemClass : SYSTEM_CLASSES) {
      context.addSystemClass(systemClass);
    }
    for (String systemClass : SERVER_CLASSES) {
      context.addServerClass(systemClass);
    }
  }

  public void configure(WebAppContext context) throws Exception {
    ClassLoader loader = context.getClassLoader();
    // Ensure the API can be loaded
    loader.loadClass(GAE_CHECK_CLASS);
  }
}

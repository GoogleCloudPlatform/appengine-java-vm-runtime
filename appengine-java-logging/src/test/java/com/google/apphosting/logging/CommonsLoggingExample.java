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

package com.google.apphosting.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ThreadLocalRandom;

class CommonsLoggingExample implements Runnable {
  private static final Log LOG = LogFactory.getLog(CommonsLoggingExample.class);

  @Override
  public void run() {
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    LOG.trace(String.format("A CommonsLogging Trace Event: %d", rand.nextInt()));
    LOG.debug(String.format("A CommonsLogging Debug Event: %d", rand.nextInt()));
    LOG.info(String.format("A CommonsLogging Info Event: %d", rand.nextInt()));
    LOG.warn(String.format("A CommonsLogging Warn Event: %d", rand.nextInt()));
    LOG.error(String.format("A CommonsLogging Error Event: %d", rand.nextInt()),
        new RuntimeException("Generic Error"));
  }
}
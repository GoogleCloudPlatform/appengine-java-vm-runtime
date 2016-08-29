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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

class Slf4jExample implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(Slf4jExample.class);

  @Override
  public void run() {
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    logger.trace("A Slf4j Trace Event: {}", rand.nextInt());
    logger.debug("A Slf4j Debug Event: {}", rand.nextInt());
    logger.info("A Slf4j Info Event: {}", rand.nextInt());
    logger.warn("A Slf4j Warn Event: {}", rand.nextInt());
    logger.error(String.format("A Slf4j Error Event: %d", rand.nextInt()),
        new RuntimeException("Generic Error"));
  }
}

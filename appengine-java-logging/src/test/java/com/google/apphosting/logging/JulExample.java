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

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JulExample implements Runnable {
  public static final Logger LOG = Logger.getLogger(JulExample.class.getName());

  @Override
  public void run() {
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    LOG.log(Level.FINEST, "A JUL Finest Event: {0}", rand.nextInt());
    LOG.log(Level.FINER, "A JUL Finer Event: {0}", rand.nextInt());
    LOG.log(Level.FINE, "A JUL Fine Event: {0}", rand.nextInt());
    LOG.log(Level.CONFIG, "A JUL Config Event: {0}", rand.nextInt());
    LOG.log(Level.INFO, "A JUL Info Event: {0}", rand.nextInt());
    LOG.log(Level.WARNING, "A JUL Warning Event: {0}", rand.nextInt());
    LOG.log(Level.SEVERE, String.format("A JUL Severe Event: %d", rand.nextInt()),
        new RuntimeException("Generic Error"));
  }
}
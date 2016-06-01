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

package com.google.apphosting.vmruntime;

import com.google.apphosting.runtime.timer.AbstractIntervalTimer;

/**
 * Minimal implementation of com.google.apphosting.runtime.timer.Timer using only the system clock.
 *
 */
public class VmTimer extends AbstractIntervalTimer {

  /*
   * (non-Javadoc)
   *
   * @see com.google.apphosting.runtime.timer.AbstractIntervalTimer#getCurrent()
   */
  @Override
  protected long getCurrent() {
    return System.nanoTime();
  }
}

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

package com.google.apphosting.runtime.jetty9;

import org.eclipse.jetty.server.session.AbstractSessionIdManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Minimal implementation of the SessionIdManager, just enough to generate new session ids.
 */
public class NoOpSessionIdManager extends AbstractSessionIdManager {

  @Override
  public boolean idInUse(String id) {
    return false;
  }

  @Override
  public void addSession(HttpSession session) {}

  @Override
  public void removeSession(HttpSession session) {}

  @Override
  public void invalidateAll(String id) {}

  @Override
  public void renewSessionId(String oldClusterId, String oldNodeId, HttpServletRequest request) {
    //don't support this
  }
}

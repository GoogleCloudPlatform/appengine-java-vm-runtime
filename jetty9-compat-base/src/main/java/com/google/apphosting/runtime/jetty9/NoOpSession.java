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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

/**
 * A minimal implementation of HttpSession, just sufficient to use attributes within a
 * single request.
 */
public class NoOpSession implements HttpSession {

  protected ServletContext context;
  protected String clusterId;
  protected String nodeId;
  protected boolean valid = true;
  private final Map<String, Object> attributes = new HashMap<>();

  /**
   * @param id the identity of the session
   * @param nodeId the id of the session qualified with the node workername
   * @param context the ServletContext to which the session belongs
   */
  protected NoOpSession(String id, String nodeId, ServletContext context) {
    this.clusterId = id;
    this.nodeId = nodeId;
    this.context = context;
  }

  /**
   * Finish a request
   */
  protected void complete() {
    synchronized (this) {
      attributes.clear();
    }
  }

  @Override
  public long getCreationTime() {
    checkValid();
    return 0;
  }

  @Override
  public String getId() {
    return clusterId;
  }

  /**
   * @return the session id with worker suffix
   */
  protected String getNodeId() {
    return nodeId;
  }

  @Override
  public long getLastAccessedTime() {
    checkValid();
    return 0;
  }

  @Override
  public ServletContext getServletContext() {
    return context;
  }

  @Override
  public void setMaxInactiveInterval(int interval) {}

  @Override
  public int getMaxInactiveInterval() {
    return 0;
  }

  @Deprecated
  @Override
  public HttpSessionContext getSessionContext() {
    checkValid();
    return null;
  }

  @Override
  public Object getAttribute(String name) {
    synchronized (this) {
      checkValid();
      return attributes.get(name);
    }
  }

  @Deprecated
  @Override
  public Object getValue(String name) {
    return getAttribute(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    synchronized (this) {
      checkValid();
      List<String> names = new ArrayList<>(attributes.keySet());
      return Collections.enumeration(names);
    }
  }

  @Deprecated
  @Override
  public String[] getValueNames() {
    synchronized (this) {
      checkValid();
      List<String> names = new ArrayList<>(attributes.keySet());
      if (names.isEmpty()) {
        return new String[0];
      }
      return names.toArray(new String[names.size()]);
    }
  }

  @Override
  public void setAttribute(String name, Object value) {
    synchronized (this) {
      checkValid();
      attributes.put(name, value);
    }
  }

  @Deprecated
  @Override
  public void putValue(String name, Object value) {
    setAttribute(name, value);
  }

  @Override
  public void removeAttribute(String name) {
    synchronized (this) {
      checkValid();
      attributes.remove(name);
    }
  }

  @Deprecated
  @Override
  public void removeValue(String name) {
    removeAttribute(name);
  }

  @Override
  public void invalidate() {
    synchronized (this) {
      checkValid();
      valid = false;
      attributes.clear();
    }
  }

  /**
   * @return true if the session has not been invalidated
   */
  protected boolean isValid() {
    return valid;
  }

  @Override
  public boolean isNew() {
    checkValid();
    return true;
  }

  private void checkValid() throws IllegalStateException {
    if (!isValid()) {
      throw new IllegalStateException("Session " + clusterId + " invalid");
    }
  }
}

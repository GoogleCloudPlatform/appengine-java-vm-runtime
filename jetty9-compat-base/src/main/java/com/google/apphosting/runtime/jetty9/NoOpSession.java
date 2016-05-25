/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS-IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
 * NoOpSession
 *
 * A minimal implementation of HttpSession, just sufficient to keep and id attributes within a
 * single request.
 */
public class NoOpSession implements HttpSession {

  protected ServletContext _context;
  protected String _clusterId;
  protected String _nodeId;
  protected boolean _valid = true;
  private final Map<String, Object> _attributes = new HashMap<String, Object>();

  /**
   * @param id
   * @param nodeId
   * @param context
   */
  protected NoOpSession(String id, String nodeId, ServletContext context) {
    _clusterId = id;
    _nodeId = nodeId;
    _context = context;
  }

  /**
   * Finish a request
   */
  public void complete() {
    _attributes.clear();
  }


  /**
   * @see javax.servlet.http.HttpSession#getCreationTime()
   */
  @Override
  public long getCreationTime() {
    return 0;
  }


  @Override
  public String getId() {
    return _clusterId;
  }

  /**
   * @return the session id with worker suffix
   */
  public String getNodeId() {
    return _nodeId;
  }


  @Override
  public long getLastAccessedTime() {
    return 0;
  }


  @Override
  public ServletContext getServletContext() {
    return _context;
  }


  @Override
  public void setMaxInactiveInterval(int interval) {
  }


  @Override
  public int getMaxInactiveInterval() {
    return 0;
  }


  @Override
  public HttpSessionContext getSessionContext() {
    return null;
  }


  @Override
  public Object getAttribute(String name) {
    return _attributes.get(name);
  }


  @Override
  public Object getValue(String name) {
    return getAttribute(name);
  }


  @Override
  public Enumeration<String> getAttributeNames() {
    List<String> names = _attributes == null
      ? Collections.EMPTY_LIST
      : new ArrayList<String>(_attributes.keySet());
    return Collections.enumeration(names);
  }


  @Override
  public String[] getValueNames() {
    List<String> names = _attributes == null
      ? Collections.EMPTY_LIST
      : new ArrayList<String>(_attributes.keySet());
    if (names.isEmpty()) {
      return null;
    }
    return names.toArray(new String[names.size()]);
  }


  @Override
  public void setAttribute(String name, Object value) {
    _attributes.put(name, value);
  }


  @Override
  public void putValue(String name, Object value) {
    setAttribute(name, value);
  }


  @Override
  public void removeAttribute(String name) {
    _attributes.remove(name);
  }


  @Override
  public void removeValue(String name) {
    removeAttribute(name);
  }


  @Override
  public void invalidate() {
    _valid = false;
    _attributes.clear();
  }

  /**
   * @return true if the session has not been invalidated
   */
  public boolean isValid() {
    return _valid;
  }

  @Override
  public boolean isNew() {
    return true;
  }

}

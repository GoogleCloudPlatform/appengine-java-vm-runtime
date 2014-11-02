/**
 * Copyright 2011 Google Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.apphosting.utils.jetty9;

import org.eclipse.jetty.server.session.AbstractSession;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import sun.reflect.Reflection;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * A stub session that doesn't actually allow attributes to be set, and thus
 * doesn't need time to save them, but satisifies JSP's eager use of
 * {@link HttpServletRequest#getSession()} (assuming the JSP code doesn't then
 * want a real session).
 *
 * We're not actually nulling out sessions; there should be enough to
 * allow a JSP to think sessions are there for any bookkeeping the
 * engine might want to do.  So this sets JSESSIONID, sends a cookie
 * to the user, and even allows enumaration of the attributes map and
 * looking up individual attributes.  However, an exception is thrown
 * if you try to make any mutations to the session.
 *
 *
 */
public class StubSessionManager extends AbstractSessionManager {

  @Override
  public void renewSessionId(String oldClusterId, String oldNodeId,
                             String newClusterId, String newNodeId) {
  }

  @Override
  protected void shutdownSessions() throws Exception {
  }

  /**
   * The actual session object for a stub session.  It will allow "generic"
   * operations including getAttributeNames(), but fails on any get/set/remove
   * of a specific attribute.
   */
  public class StubSession extends AbstractSession {
    private final Map<String, Object> attributes = new HashMap<String, Object>();

    protected StubSession(String id) {
      super(StubSessionManager.this, System.currentTimeMillis(), System.currentTimeMillis(), id);
    }

    public StubSession(HttpServletRequest req) {
      super(StubSessionManager.this, req);
    }

    @Override
    public Object getAttribute(String name) {
      return null;
    }

    @Override
    public void removeAttribute(String name) {
      throwException();
    }

    @Override
    public void setAttribute(String name, Object value) {
      throwException();
    }

    private void throwException() {
      if (Reflection.getCallerClass(3).getName().startsWith("org.apache.jasper")) {
        return;
      }
      throw new RuntimeException("Session support is not enabled in appengine-web.xml.  "
              + "To enable sessions, put <sessions-enabled>true</sessions-enabled> in that "
              + "file.  Without it, getSession() is allowed, but manipulation of session"
              + "attributes is not.");
    }

    @Override
    public Map<String, Object> getAttributeMap() {
      return attributes;
    }

    @Override
    public int getAttributes() {
      return 0;
    }

    @Override
    public Set<String> getNames() {
      return null;
    }

    @Override
    public void clearAttributes() {
    }

    @Override
    public Object doPutOrRemove(String string, Object o) {
      return null;
    }

    @Override
    public Object doGet(String string) {
      return null;
    }

    @Override
    public Enumeration<String> doGetAttributeNames() {
      return null;
    }
  }

  public StubSessionManager() {
    this.setSessionIdManager(new HashSessionIdManager());
  }

  @Override
  protected void addSession(AbstractSession session) {
  }

  /**
   * Gets an "existing" StubSession.  Since they can't have any data, and may
   * have been created by some other clone, we trust that it really existed and
   * just return a new session.
   *
   * @param id the id of the requested session
   * @return a new StubSession of the correct id
   */
  @Override
  public AbstractSession getSession(String id) {
    return new StubSession(id);
  }

  @Override
  public int getSessions() {
    return 0;
  }

  @Override
  protected AbstractSession newSession(HttpServletRequest req) {
    return new StubSession(req);
  }

  @Override
  protected boolean removeSession(String arg0) {
    return true;
  }

  @Override
  public boolean isUsingCookies() {
    return false;
  }
}

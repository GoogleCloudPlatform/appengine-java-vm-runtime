/**
 * Copyright 2012 Google Inc. All Rights Reserved.
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


package com.google.apphosting.runtime.jetty9;

import static com.google.appengine.repackaged.com.google.common.io.BaseEncoding.base64Url;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.apphosting.runtime.SessionData;
import com.google.apphosting.runtime.SessionStore;

import org.eclipse.jetty.server.session.AbstractSession;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.session.HashSessionIdManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Implements the Jetty {@link AbstractSessionManager} and, as an
 * inner class, {@link HashSessionIdManager} for our context.  The
 * session manager has to check the provided {@link SessionStore SessionStores}
 * to find sessions.
 *
 */
public class SessionManager extends AbstractSessionManager {
  private static final Logger logger =
      Logger.getLogger(SessionManager.class.getName());

  static final String SESSION_PREFIX = "_ahs";

  /**
   * To reduce our datastore put time, we only consider a session
   * dirty on access if it is at least 25% expired.  So a session
   * that expires in 1 hr will only be re-stored every 15 minutes,
   * unless a "real" attribute change occurs.
   */
  public static final double UPDATE_TIMESTAMP_RATIO = 0.75;

  private static String lastId = null;

  /**
   * Specializes {@link HashSessionIdManager}, featuring strong session ids.
   */
  public static class SessionIdManager extends HashSessionIdManager {

    public SessionIdManager() {
      super(new SecureRandom());
    }

    /**
     * Computes a new session key.
     * @return a string representing the session id, effectively unique to
     *    this session
     */
    @Override
    public String newSessionId(HttpServletRequest request, long created) {
      byte randomBytes[] = new byte[16];
      _random.nextBytes(randomBytes);
      String id = base64Url().omitPadding().encode(randomBytes);
      lastId = id;
      logger.fine("Created a random session identifier: " + id);
      return id;
    }
  }

  /**
   * A session implementation using the provided list of
   * {@link SessionStore SessionStores} to store attributes.  Expiration is
   * also stored.
   * <p>
   * An instance of this class may be used simultaneously be multiple
   * request threads. We use synchronization to guard access to sessionData
   * and the parent object state.
   *
   */
  public class AppEngineSession extends AbstractSession {
    private final SessionData sessionData;
    private final String key;
    private volatile boolean dirty;

    /**
     * Create a new brand new session for the specified request.  This
     * constructor saves the new session to the datastore and
     * memcache.
     */
    public AppEngineSession(HttpServletRequest request) {
      super(SessionManager.this, request);

      this.sessionData = createSession(getId());
      key = SESSION_PREFIX + getId();
      dirty = false;
    }

    /**
     * Create an object for an existing session
     */
    public AppEngineSession(long created, long accessed,
        String sessionId, SessionData sessionData) {
      super(SessionManager.this, created, accessed, sessionId);
      this.sessionData = sessionData;
      key = SESSION_PREFIX + sessionId;
      dirty = false;
    }

    public boolean isDirty() {
      return dirty;
    }

    public void save() {
      if (dirty) {
        logger.fine("Session " + getId() + " is dirty, saving.");

        int delay = 50;
        try {
          for (int attemptNum = 0; attemptNum < 10; attemptNum++) {
            try {
              synchronized (this) {
                if (dirty) {
                  for (SessionStore sessionStore : sessionStoresInWriteOrder) {
                    sessionStore.saveSession(key, sessionData);
                  }
                  dirty = false;
                }
                return;
              }
            } catch (SessionStore.Retryable retryable) {
            } catch (ApiProxy.ApiDeadlineExceededException e) {
            }
            try {
              Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
            logger.warning("Timeout while saving session " + getId() + ".");
            delay *= 2;
          }
          logger.log(Level.SEVERE, "Unable to save session " + getId() +
              " - too many attempts");
        } catch (DeadlineExceededException e) {
          logger.log(Level.SEVERE, "Unable to save session " + getId() +
                        " - too many timeouts.", e);
        }
      }
    }

    @Override
    public synchronized Object doGet(String name) {
      return sessionData.getValueMap().get(name);
    }

    @Override
    public synchronized Enumeration<String> doGetAttributeNames() {
      return Collections.enumeration(sessionData.getValueMap().keySet());
    }

    @Override
    public synchronized void setAttribute(String name, Object value) {
      super.setAttribute(name, value);
      dirty = true;
    }

    @Override
    public Object doPutOrRemove(String name, Object value) {
      return value == null ? sessionData.getValueMap().remove(name)
                             : sessionData.getValueMap().put(name, value);
    }

    @Override
    public synchronized void removeAttribute(String name) {
      super.removeAttribute(name);
      dirty = true;
    }

    @Override
    public void clearAttributes() {
      while (sessionData.getValueMap() != null && sessionData.getValueMap().size() > 0) {
        ArrayList<String> keys;
        synchronized (this) {
          keys = new ArrayList<String>(sessionData.getValueMap().keySet());
        }

        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
          String key = (String) iter.next();

          Object value;
          synchronized (this) {
            value = doPutOrRemove(key, null);
          }
          unbindValue(key, value);

          ((SessionManager) getSessionManager()).doSessionAttributeListeners(this,
              key, value, null);
        }
      }
      if (sessionData.getValueMap() != null) {
        sessionData.getValueMap().clear();
      }
    }

    @Override
    public Map<String, Object> getAttributeMap() {
      return sessionData.getValueMap();
    }

    @Override
    protected void timeout() throws IllegalStateException {
    }

    @Override
    protected boolean access(long accessTime) {
      long expirationTime = sessionData.getExpirationTime();
      long timeRemaining = expirationTime - accessTime;
      if (dirty) {
      } else if (timeRemaining < (getSessionExpirationInMilliseconds() * UPDATE_TIMESTAMP_RATIO)) {
        dirty = true;
        logger.fine("Session " + getId() + " accessed while near expiration, marking dirty.");
      } else {
        logger.fine("Session " + getId() + " accessed early, not marking dirty.");
      }
      sessionData.setExpirationTime(System.currentTimeMillis()
              + getSessionExpirationInMilliseconds());
      return super.access(accessTime);
    }

    /**
     * Check if the expiration time has passed
     *
     * @see org.eclipse.jetty.server.session.AbstractSession#checkExpiry(long)
     */
    @Override
    protected boolean checkExpiry(long time) {
      long expirationTime = sessionData.getExpirationTime();
      return (time >= expirationTime);
    }

    @Override
    public synchronized void invalidate() throws IllegalStateException {
      super.invalidate();
    }

    void deleteSession() {
      for (SessionStore sessionStore : sessionStoresInWriteOrder) {
        sessionStore.deleteSession(key);
      }
    }

    @Override
    public int getAttributes() {
      return 0;
    }

    @Override
    public synchronized Set<String> getNames() {
      return new HashSet<String>(sessionData.getValueMap().keySet());
    }
  }

  private final List<SessionStore> sessionStoresInWriteOrder;
  private final List<SessionStore> sessionStoresInReadOrder;

  static String lastId() {
    return lastId;
  }

  /**
   * Constructs a SessionManager
   *
   * @param sessionStoresInWriteOrder The SessionStores in the order to which
   * they should be written.  When attempting to load a session, the read order
   * is the opposite of the write order, so if the write order is A, B, C, when
   * reading we will first consult C, and if the session is not found move on
   * to B, and if not found then on to A.
   */
  public SessionManager(List<SessionStore> sessionStoresInWriteOrder) {
    super();
    setSessionIdManager(new SessionIdManager());
    this.sessionStoresInWriteOrder = sessionStoresInWriteOrder;
    this.sessionStoresInReadOrder = new ArrayList<SessionStore>(sessionStoresInWriteOrder);
    Collections.reverse(this.sessionStoresInReadOrder);
  }

  @Override
  protected AppEngineSession newSession(HttpServletRequest request) {
    return new AppEngineSession(request);
  }

  @Override
  public AppEngineSession getSession(String sessionId) {
    SessionData data = loadSession(sessionId);
    if (data != null) {
      long time = System.currentTimeMillis();
      return new AppEngineSession(time, time, sessionId, data);
    } else {
      return null;
    }
  }

  SessionData loadSession(String sessionId) {
    String key = SESSION_PREFIX + sessionId;

    SessionData data = null;
    for (SessionStore sessionStore : sessionStoresInReadOrder) {
      try {
        data = sessionStore.getSession(key);
        if (data != null) {
          break;
        }
      } catch (RuntimeException e) {
        String msg = "Exception while loading session data";
        logger.log(Level.WARNING, msg, e);
        if (ApiProxy.getCurrentEnvironment() != null) {
          ApiProxy.log(createWarningLogRecord(msg, e));
        }
        break;
      }
    }
    if (data != null) {
      if (System.currentTimeMillis() > data.getExpirationTime()) {
        logger.fine("Session " + sessionId + " expired " +
                    ((System.currentTimeMillis() - data.getExpirationTime()) / 1000) +
                    " seconds ago, ignoring.");
        return null;
      }
    }
    return data;
  }

  SessionData createSession(String sessionId) {
    String key = SESSION_PREFIX + sessionId;
    SessionData data = new SessionData();
    data.setExpirationTime(System.currentTimeMillis() + getSessionExpirationInMilliseconds());
    for (SessionStore sessionStore : sessionStoresInWriteOrder) {
      try {
        sessionStore.saveSession(key, data);
      } catch (SessionStore.Retryable retryable) {
        throw retryable.getCause();
      }
    }
    return data;
  }

  private long getSessionExpirationInMilliseconds() {
    long seconds = getMaxInactiveInterval();
    if (seconds < 0) {
      return Integer.MAX_VALUE * 1000L;
    } else {
      return seconds * 1000;
    }
  }

  private LogRecord createWarningLogRecord(String message, Throwable ex) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    printWriter.println(message);
    if (ex != null) {
      ex.printStackTrace(printWriter);
    }

    return new LogRecord(LogRecord.Level.warn,
            System.currentTimeMillis() * 1000,
            stringWriter.toString());
  }

  @Override
  protected void addSession(AbstractSession session) {
  }

  @Override
  protected boolean removeSession(String clusterId) {
    AppEngineSession session = getSession(clusterId);
    if (session != null) {
      session.deleteSession();
      return true;
    }
    return false;
  }

  @Override
  protected void shutdownSessions() throws Exception {
  }
}

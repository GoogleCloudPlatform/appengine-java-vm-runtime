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
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;

/**
 * Implements the Jetty {@link AbstractSessionManager} and, as an inner class,
 * {@link HashSessionIdManager} for our context. The session manager has to check the provided
 * {@link SessionStore SessionStores} to find sessions.
 */
public class SessionManager extends AbstractSessionManager {
  private static final Logger logger = Logger.getLogger(SessionManager.class.getName());

  static final String SESSION_PREFIX = "_ahs";

  /**
   * To reduce our datastore put time, we only consider a session dirty on access if it is at least
   * 25% expired. So a session that expires in 1 hr will only be re-stored every 15 minutes, unless
   * a "real" attribute change occurs.
   */
  public static final double UPDATE_TIMESTAMP_RATIO = 0.75;

  /* This is just useful for testing, and cheap to hold... */
  private static String lastId = null;

  /**
   * Specializes {@link HashSessionIdManager}, featuring strong session ids.
   */
  // Ludo: not sure that this session id generator is any better than the
  // AbstractSessionIdManager's generation algorithm, which is also based on the
  // SecureRandom class.
  public static class SessionIdManager extends HashSessionIdManager {

    public SessionIdManager() {
      super(new SecureRandom());
    }

    /**
     * Computes a new session key.
     *
     * @return a string representing the session id, effectively unique to this session
     */
    @Override
    public String newSessionId(HttpServletRequest request, long created) {
      return generateNewId();
    }

    @Override
    public String newSessionId(long seedTerm) {
      return generateNewId();
    }

    public String generateNewId() {
      byte[] randomBytes = new byte[16];
      _random.nextBytes(randomBytes);
      // Use a web-safe encoding in case the session identifier gets
      // passed via a URL path parameter.
      String id = base64Url().omitPadding().encode(randomBytes);
      lastId = id;
      logger.fine("Created a random session identifier: " + id);
      return id;
    }
  }

  /**
   * <p>
   * A session implementation using the provided list of {@link SessionStore SessionStores} to store
   * attributes. Expiration is also stored. An instance of this class may be used simultaneously
   * be multiple request threads. We use synchronization to guard access to sessionData and the
   * parent object state.
   * </p>
   */
  public class AppEngineSession extends AbstractSession {

    private final SessionData sessionData;
    private String key;
    private volatile boolean dirty;

    /**
     * Create a new brand new session for the specified request. This constructor saves the new
     * session to the datastore and memcache.
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
    public AppEngineSession(
        long created, long accessed, String sessionId, SessionData sessionData) {
      super(SessionManager.this, created, accessed, sessionId);
      this.sessionData = sessionData;
      key = SESSION_PREFIX + sessionId;
      dirty = false;
    }

    public boolean isDirty() {
      return dirty;
    }

    @Override
    public void renewId(HttpServletRequest request) {
      final String oldId = getClusterId();

      // remove session with the old from storage
      deleteSession();

      // generate a new id
      String newClusterId =
          ((SessionIdManager) getSessionManager().getSessionIdManager())
              .newSessionId(request.hashCode());
      String newNodeId =
          ((SessionIdManager) getSessionManager().getSessionIdManager())
              .getNodeId(newClusterId, request);

      // change the ids and recreate the key
      setClusterId(newClusterId);
      setNodeId(newNodeId);
      key = SESSION_PREFIX + newClusterId;

      // save the session with the new id
      save(true);

      setIdChanged(true);

      ((SessionManager) getSessionManager()).callSessionIdListeners(this, oldId);
    }

    public void save() {
      save(false);
    }

    protected void save(boolean force) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine(String.format("Session %s dirty=%b force=%b", getId(), dirty, force));
      }

      // save if it is dirty or its a forced save
      if (force || dirty) {
        int delay = 1000; // Start with a delay of 1s as per SLA if a put fails.
        try {
          // Try 6 times with exponential back-off. The sixth time the
          // delay will be about 32 seconds. We need to eventually give
          // up because it is possible the Datastore API is totally hosed
          // and we want the request to eventually terminate.
          for (int attemptNum = 0; attemptNum < 6; attemptNum++) {
            try {
              synchronized (this) {
                if (dirty || force) {
                  for (SessionStore sessionStore : sessionStoresInWriteOrder) {
                    sessionStore.saveSession(key, sessionData);
                  }
                  dirty = false;
                  if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("Session %s saved", getId()));
                  }
                  return;
                }
              }
            } catch (SessionStore.Retryable retryable) {
              // Don't break out of the loop
            } catch (ApiProxy.ApiDeadlineExceededException e) {
              // Don't break out of the loop
            }
            try {
              Thread.sleep(delay);
            } catch (InterruptedException e) {
              // Just try again prematurely
            }
            logger.log(Level.WARNING, String.format("Session %s timeout while saving", getId()));
            delay *= 2;
          }
          logger.log(
              Level.SEVERE, String.format("Session %s not saved: too many attempts", getId()));
        } catch (DeadlineExceededException e) {
          logger.log(
              Level.SEVERE, String.format("Session %s not saved: too many timeouts", getId()), e);
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
      return value == null
          ? sessionData.getValueMap().remove(name)
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
          String key = iter.next();

          Object value;
          synchronized (this) {
            value = doPutOrRemove(key, null);
          }
          unbindValue(key, value);

          ((SessionManager) getSessionManager())
              .doSessionAttributeListeners(this, key, value, null);
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
      // TODO(user) only called by a session scavenger thread which appengine does not have.
      // Sessions are only checked for expiry when a request comes in for them. If the
      // SessionData is expired in the datastore, then getSession() returns null.
    }

    @Override
    protected boolean access(long accessTime) {
      // Optimize flushing of session data to persistent storage based on nearness to expiry time.
      long expirationTime = sessionData.getExpirationTime();
      long timeRemaining = expirationTime - accessTime;
      if (!dirty) {
        if (timeRemaining < (getSessionExpirationInMilliseconds() * UPDATE_TIMESTAMP_RATIO)) {
          dirty = true;
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                String.format(
                    "Session %s accessed while near expiration, marking dirty.", getId()));
          }
        } else {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("Session %s accessed early, not marking dirty.", getId()));
          }
        }
      }
      sessionData.setExpirationTime(
          System.currentTimeMillis() + getSessionExpirationInMilliseconds());
      // Handle session being invalid, update number of requests inside session.
      return super.access(accessTime);
    }

    /**
     * Check if the expiration time has passed.
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

  /* used in tests, thus package-protected */
  static String lastId() {
    return lastId;
  }

  /**
   * Constructs a SessionManager
   *
   * @param sessionStoresInWriteOrder The SessionStores in the order to which they should be
   *        written. When attempting to load a session, the read order is the opposite of the write
   *        order, so if the write order is A, B, C, when reading we will first consult C, and if
   *        the session is not found move on to B, and if not found then on to A.
   */
  public SessionManager(List<SessionStore> sessionStoresInWriteOrder) {
    super();
    this.sessionStoresInWriteOrder = sessionStoresInWriteOrder;
    // We'll always read in the opposite order we write, so create a copy
    // of the stores in write order and then reverse it.
    this.sessionStoresInReadOrder = new ArrayList<SessionStore>(sessionStoresInWriteOrder);
    Collections.reverse(this.sessionStoresInReadOrder);

    // NOTE: this breaks the standard jetty contract that a single server has a single
    // SessionIdManager
    // but there is 1 SessionManager per context.
    _sessionIdManager = new SessionIdManager();
  }

  @Override
  protected AppEngineSession newSession(HttpServletRequest request) {
    // This will save the session persistently.
    return new AppEngineSession(request);
  }

  @Override
  public AppEngineSession getSession(String sessionId) {
    SessionData data = loadSession(sessionId);
    if (data != null) {
      // Make access time same as create time.
      long time = System.currentTimeMillis();
      return new AppEngineSession(time, time, sessionId, data);
    } else {
      return null;
    }
  }

  SessionData loadSession(String sessionId) {
    String key = SESSION_PREFIX + sessionId;

    SessionData data = null;
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(String.format("Session %s load", sessionId));
    }
    for (SessionStore sessionStore : sessionStoresInReadOrder) {
      // Keep iterating until we find a store that has the session data we
      // want.
      try {
        data = sessionStore.getSession(key);
        if (data != null) {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("Session %s loaded from %s", sessionId, sessionStore));
          }
          break;
        }
      } catch (RuntimeException e) {
        logger.log(Level.WARNING, String.format("Session %s load exception", sessionId), e);
        if (ApiProxy.getCurrentEnvironment() != null) {
          ApiProxy.log(createWarningLogRecord("Exception while loading session data", e));
        }
        break;
      }
    }
    if (data != null) {
      if (System.currentTimeMillis() > data.getExpirationTime()) {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine(
              String.format(
                  "Session %s expired %d seconds ago, ignoring",
                  sessionId,
                  (System.currentTimeMillis() - data.getExpirationTime()) / 1000));
        }
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
        // rethrowing the cause to maintain backwards compatibility
        throw retryable.getCause();
      }
    }
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(String.format("Session %s created", sessionId));
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

    return new LogRecord(
        LogRecord.Level.warn, System.currentTimeMillis() * 1000, stringWriter.toString());
  }

  @Override
  public void doStart() throws Exception {
    // always use our special id manager one
    _sessionIdManager.start();
    addBean(_sessionIdManager, true);

    super.doStart();
  }

  @Override
  protected void addSession(AbstractSession session) {
    // No list of sessions is kept in memory, so do nothing here.
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
    // Called when the session manager is stopping. We don't need to do anything here.
  }

  /**
   * Not used.
   */
  @Override
  public void renewSessionId(
      String oldClusterId, String oldNodeId, String newClusterId, String newNodeId) {

    // Not used. See instead AppEngineSession.renewId

  }

  /**
   * Call any session id listeners registered. Usually done by renewSessionId() method, but that is
   * not used in appengine.
   */
  public void callSessionIdListeners(AbstractSession session, String oldId) {
    HttpSessionEvent event = new HttpSessionEvent(session);
    for (HttpSessionIdListener l : _sessionIdListeners) {
      l.sessionIdChanged(event, oldId);
    }
  }
}

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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.DeferredTaskCallback;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.TaskCountDownLatch;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.runtime.DatastoreSessionStore;
import com.google.apphosting.runtime.DeferredDatastoreSessionStore;
import com.google.apphosting.runtime.MemcacheSessionStore;
import com.google.apphosting.runtime.SessionData;
import com.google.apphosting.runtime.SessionManagerUtil;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.jetty9.SessionManager.AppEngineSession;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Tests for SessionManager and its inner classes.
 *
 * @author fabbott@google.com (Freeland Abbott)
 */
public class SessionManagerIT extends TestCase {

  private static final int SESSION_EXPIRATION_SECONDS = 60;

  private MemcacheService memcache;
  private DatastoreService datastore;
  private SessionManager manager;
  private LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalDatastoreServiceTestConfig(), new LocalMemcacheServiceTestConfig());

  private class TimeoutGeneratingDelegate implements Delegate<ApiProxy.Environment> {
    private final Delegate delegate;
    private int timeoutCount = 0;

    private TimeoutGeneratingDelegate(Delegate delegate) {
      this.delegate = delegate;
    }

    public void setTimeouts(int count) {
      timeoutCount = count;
    }

    public int getTimeoutsRemaining() {
      return timeoutCount;
    }

    @SuppressWarnings("unchecked")
    public byte[] makeSyncCall(
        ApiProxy.Environment environment, String packageName, String methodName, byte[] request)
        throws ApiProxyException {
      if (packageName.equals("datastore_v3") && timeoutCount > 0) {
        timeoutCount--;
        throw new DatastoreTimeoutException("Timeout");
      }
      return delegate.makeSyncCall(environment, packageName, methodName, request);
    }

    public Future<byte[]> makeAsyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      if (packageName.equals("datastore_v3") && timeoutCount > 0) {
        timeoutCount--;
        throw new DatastoreTimeoutException("Timeout");
      }
      return delegate.makeAsyncCall(environment, packageName, methodName, request, apiConfig);
    }

    @SuppressWarnings("unchecked")
    public void log(ApiProxy.Environment environment, LogRecord record) {
      delegate.log(environment, record);
    }

    @SuppressWarnings("unchecked")
    public void flushLogs(ApiProxy.Environment environment) {
      delegate.flushLogs(environment);
    }

    public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
      return null;
    }
  }

  public String startNamespace() {
    return "";
  }

  public String testNamespace() {
    return "";
  }

  public static class NamespacedStartTest extends SessionManagerIT {
    @Override
    public String startNamespace() {
      return "start-namespace";
    }

    @Override
    public String testNamespace() {
      return "";
    }
  }

  public static class NamespacedTest extends SessionManagerIT {
    @Override
    public String startNamespace() {
      return "";
    }

    @Override
    public String testNamespace() {
      return "test-namespace";
    }
  }

  public static class NamespacedTestTest extends SessionManagerIT {
    @Override
    public String startNamespace() {
      return "start-namespace";
    }

    @Override
    public String testNamespace() {
      return "test-namespace";
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    helper.setUp();
    memcache = MemcacheServiceFactory.getMemcacheService("");
    datastore = DatastoreServiceFactory.getDatastoreService();
    NamespaceManager.set(startNamespace());
    manager =
        new SessionManager(Arrays.asList(new DatastoreSessionStore(), new MemcacheSessionStore()));
    NamespaceManager.set(testNamespace());
  }

  @Override
  public void tearDown() throws Exception {
    helper.tearDown();
    super.tearDown();
  }

  public void testIdGeneration() {
    long timestamp = System.currentTimeMillis();

    SessionManager.SessionIdManager idManager = new SessionManager.SessionIdManager();
    HttpServletRequest mockRequest = makeMockRequest(false);
    replay(mockRequest);
    String sessionid = idManager.newSessionId(mockRequest, timestamp);
    assertNotNull(sessionid);
    assertTrue(sessionid.length() > 0);
    verify(mockRequest);

    mockRequest = makeMockRequest(false);
    replay(mockRequest);
    String sessionid2 = idManager.newSessionId(mockRequest, timestamp);
    assertNotNull(sessionid2);
    assertTrue(sessionid2.length() > 0);
    assertFalse(sessionid.equals(sessionid2));
    verify(mockRequest);
  }

  @SuppressWarnings("unchecked")
  public void testNewSession() throws EntityNotFoundException {
    assertTrue(manager.getSessionIdManager() instanceof SessionManager.SessionIdManager);
    manager.setMaxInactiveInterval(SESSION_EXPIRATION_SECONDS);

    HttpServletRequest request = makeMockRequest(true);
    replay(request);

    AppEngineSession session = manager.newSession(request);
    assertNotNull(session);
    assertTrue(session instanceof SessionManager.AppEngineSession);
    assertEquals(SessionManager.lastId(), session.getId());

    session.setAttribute("foo", "bar");
    session.save();
  }

  @SuppressWarnings("unchecked")
  public void testGetSessionSameSessionManager() throws EntityNotFoundException {
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    session.setAttribute("foo", "bar");
    assertEquals("bar", session.getAttribute("foo"));
    session.save();

    HttpSession session2 = manager.getSession(session.getId());
    assertEquals(session.getId(), session2.getId());
    assertEquals("bar", session2.getAttribute("foo"));
  }

  @SuppressWarnings("unchecked")
  public void testGetSessionFromMemcache() throws EntityNotFoundException {
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    session.setAttribute("foo", "bar");
    session.save();

    // Ensure we're really fetching from memcache
    SessionStore explosiveStore = EasyMock.createMock(SessionStore.class);
    manager = new SessionManager(Arrays.asList(explosiveStore, new MemcacheSessionStore()));
    HttpSession session2 = manager.getSession(session.getId());
    assertEquals(session.getId(), session2.getId());
    assertEquals("bar", session2.getAttribute("foo"));
  }

  public void testRenewSessionId() throws Exception {
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    session.setAttribute("foo", "bar");
    session.save();
    String oldId = session.getId();
    byte[] bytes = (byte[]) memcache.get(SessionManager.SESSION_PREFIX + oldId);
    assertNotNull(bytes);
    SessionData data = (SessionData) SessionManagerUtil.deserialize(bytes);
    assertEquals("bar", data.getValueMap().get("foo"));

    //renew session id
    session.renewId(request);

    //Ensure we deleted the session with the old id
    AppEngineSession sessionx = manager.getSession(oldId);
    assertNull(sessionx);
    assertNull(memcache.get(SessionManager.SESSION_PREFIX + oldId));

    //Ensure we changed the id
    String newId = session.getId();
    assertNotSame(oldId, newId);

    //Ensure we stored the session with the new id
    AppEngineSession session2 = manager.getSession(newId);
    assertNotSame(session, session2);
    assertEquals("bar", session2.getAttribute("foo"));
    bytes = (byte[]) memcache.get(SessionManager.SESSION_PREFIX + newId);
    assertNotNull(bytes);
    data = (SessionData) SessionManagerUtil.deserialize(bytes);
    assertEquals("bar", data.getValueMap().get("foo"));

    //Test we can store attributes
    session2.setAttribute("one", "two");
    session2.save();
    bytes = (byte[]) memcache.get(SessionManager.SESSION_PREFIX + newId);
    assertNotNull(bytes);
    data = (SessionData) SessionManagerUtil.deserialize(bytes);
    assertEquals("two", data.getValueMap().get("one"));
  }

  private AppEngineSession createSession() {
    NamespaceManager.set(startNamespace());
    SessionManager localManager =
        new SessionManager(Arrays.asList(new DatastoreSessionStore(), new MemcacheSessionStore()));
    NamespaceManager.set(testNamespace());
    HttpServletRequest request = makeMockRequest(true);

    return localManager.newSession(request);
  }

  private HttpSession retrieveSession(AppEngineSession session) {
    NamespaceManager.set(startNamespace());
    SessionManager manager =
        new SessionManager(Arrays.asList(new DatastoreSessionStore(), new MemcacheSessionStore()));
    try {
      return manager.getSession(session.getId());
    } finally {
      NamespaceManager.set(testNamespace());
    }
  }

  private void testSerialize(String key, Object value) {
    AppEngineSession session = createSession();
    session.setAttribute(key, value);
    session.save();

    HttpSession httpSession = retrieveSession(session);
    assertNotNull(httpSession);
    Object result = httpSession.getAttribute(key);

    if (!value.getClass().isArray()) {
      assertEquals(value, result);
    } else {
      if (value instanceof Object[]) {
        Object[] valueAsArray = (Object[]) value;
        Object[] resultAsArray = (Object[]) result;

        if (!Arrays.deepEquals(valueAsArray, resultAsArray)) {
          throw new AssertionFailedError(
              "Expected: "
                  + Arrays.deepToString(valueAsArray)
                  + " Received: "
                  + Arrays.deepToString(resultAsArray));
        }
      } else if (value instanceof byte[]) {
        assertArrayEquals((byte[]) value, (byte[]) result);
      } else {
        throw new RuntimeException("Unhandled array type, " + value.getClass());
      }
    }
  }

  private static class SomeSerializable implements Serializable {
    int value;

    public SomeSerializable(int value) {
      this.value = value;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof SomeSerializable)) {
        return false;
      }
      return ((SomeSerializable) obj).value == value;
    }
  }

  public void testSessionSerialization() throws EntityNotFoundException {
    testSerialize("key", "value");
    testSerialize("key", 1);
    testSerialize("key", Math.PI);
    testSerialize("key", new byte[] {1, 2, 3});
    testSerialize("key", new String[] {"hello", "world"});
    testSerialize(
        "key",
        new String[][] {
          new String[] {"hello", "I"}, new String[] {"love", "you"}, new String[] {"won't", "you"},
        });
    testSerialize("key", new SomeSerializable(42));
    testSerialize(
        "key",
        new Object[] {
          new SomeSerializable(1), new SomeSerializable(2), new SomeSerializable(3),
        });
    testSerialize(
        "key",
        new SomeSerializable[] {
          new SomeSerializable(1), new SomeSerializable(2), new SomeSerializable(3),
        });
    testSerialize("key", float.class);
    Map<String, String> map = new HashMap<>();
    map.put("a", "b");
    map.put("1", "2");
    testSerialize("key", map);

    // TODO(schwardo) or TODO(fabbot)
    // Consider adding some cross-ClassLoader tests here.
  }

  private static class NonDeserializable implements Serializable {
    int value;

    public NonDeserializable(int value) {
      this.value = value;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof NonDeserializable)) {
        return false;
      }
      return ((NonDeserializable) obj).value == value;
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      s.defaultReadObject();
      throw new ClassNotFoundException("cannot deserialize object");
    }
  }

  public void testNonDeserializableSession() throws Exception {
    try (SuppressLogging logging =
        new SuppressLogging(
            SessionManager.class.getName(),
            "com.google.appengine.tools.development.ApiProxyLocalImpl")) {
      AppEngineSession session = createSession();
      session.setAttribute("key", new NonDeserializable(1));
      session.save();

      assertNull(retrieveSession(session));
    }
  }

  public void testSessionNotAvailableInMemcache() throws EntityNotFoundException {
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    session.setAttribute("foo", "bar");
    session.save();

    memcache.clearAll();
    manager =
        new SessionManager(Arrays.asList(new DatastoreSessionStore(), new MemcacheSessionStore()));
    HttpSession session2 = manager.getSession(session.getId());
    assertEquals(session.getId(), session2.getId());
    assertEquals("bar", session2.getAttribute("foo"));

    manager =
        new SessionManager(Collections.<SessionStore>singletonList(new MemcacheSessionStore()));
    assertNull(manager.getSession(session.getId()));
  }

  public void testGetSessionInvalid() throws EntityNotFoundException {
    assertTrue(manager.getSessionIdManager() instanceof SessionManager.SessionIdManager);
    manager.setMaxInactiveInterval(SESSION_EXPIRATION_SECONDS);

    HttpSession session = manager.getSession("12345");
    assertEquals(null, session);
  }

  @SuppressWarnings("unchecked")
  public void testDatastoreTimeouts() throws EntityNotFoundException {
    Delegate original = ApiProxy.getDelegate();
    // Throw in a couple of datastore timeouts
    TimeoutGeneratingDelegate newDelegate = new TimeoutGeneratingDelegate(original);
    try {
      ApiProxy.setDelegate(newDelegate);

      HttpServletRequest request = makeMockRequest(true);
      replay(request);
      AppEngineSession session = manager.newSession(request);
      session.setAttribute("foo", "bar");
      newDelegate.setTimeouts(3);
      session.save();
      assertEquals(newDelegate.getTimeoutsRemaining(), 0);

      memcache.clearAll();
      manager =
          new SessionManager(Collections.<SessionStore>singletonList(new DatastoreSessionStore()));
      HttpSession session2 = manager.getSession(session.getId());
      assertEquals(session.getId(), session2.getId());
      assertEquals("bar", session2.getAttribute("foo"));
    } finally {
      ApiProxy.setDelegate(original);
    }
  }

  public void testMemcacheOnlyLifecycle() {
    manager =
        new SessionManager(Collections.<SessionStore>singletonList(new MemcacheSessionStore()));
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    session.setAttribute("foo", "bar");
    session.save();
    assertNotNull(memcache.get(SessionManager.SESSION_PREFIX + session.getId()));
    HttpSession session2 = manager.getSession(session.getId());
    assertEquals(session.getId(), session2.getId());
    assertEquals("bar", session2.getAttribute("foo"));
  }

  /**
   * public void testDatastoreOnlyLifecycle() throws EntityNotFoundException {
   * manager =
   * new SessionManager(Collections.&lt;SessionStore&gt;singletonList(new DatastoreSessionStore()));
   * HttpServletRequest request = makeMockRequest(true);
   * replay(request);
   * <p>
   * AppEngineSession session = manager.newSession(request);
   * session.setAttribute("foo", "bar");
   * session.save();
   * Key key = KeyFactory.createKey("_ah_SESSION", SessionManager.SESSION_PREFIX + session.getId());
   * datastore.get(key);
   * HttpSession session2 = manager.getSession(session.getId());
   * assertEquals(session.getId(), session2.getId());
   * assertEquals("bar", session2.getAttribute("foo"));
   * }
   */
  public void testDeferredDatastoreSessionStore()
      throws InterruptedException, IOException, ClassNotFoundException, EntityNotFoundException {
    helper.tearDown();
    TaskCountDownLatch latch = new TaskCountDownLatch(1);
    helper =
        new LocalServiceTestHelper(
            new LocalTaskQueueTestConfig()
                .setCallbackClass(DeferredTaskCallback.class)
                .setTaskExecutionLatch(latch)
                .setDisableAutoTaskExecution(false),
            new LocalDatastoreServiceTestConfig());
    helper.setUp();
    manager =
        new SessionManager(
            Collections.<SessionStore>singletonList(new DeferredDatastoreSessionStore(null)));
    HttpServletRequest request = makeMockRequest(true);
    replay(request);
    AppEngineSession session = manager.newSession(request);
    // Wait for the session creation task.
    assertTrue(latch.awaitAndReset(10, TimeUnit.SECONDS));
    session.setAttribute("foo", "bar");
    session.save();
    // Wait for the session update task.
    assertTrue(latch.awaitAndReset(10, TimeUnit.SECONDS));
    Key key = KeyFactory.createKey("_ah_SESSION", SessionManager.SESSION_PREFIX + session.getId());
    datastore.get(key);
    HttpSession session2 = manager.getSession(session.getId());
    assertEquals(session.getId(), session2.getId());
    assertEquals("bar", session2.getAttribute("foo"));
    session.deleteSession();
    // Wait for the session delete task.
    assertTrue(latch.awaitAndReset(10, TimeUnit.SECONDS));
    try {
      datastore.get(key);
      fail("entity should have been deleted");
    } catch (EntityNotFoundException e) {
      // good
    }
  }

  private HttpServletRequest makeMockRequest(boolean forSession) {
    HttpServletRequest mockRequest = createMock(HttpServletRequest.class);
    if (forSession) {
      expect(mockRequest.getAttribute("org.mortbay.http.ajp.JVMRoute")).andReturn(null).once();
    }

    return mockRequest;
  }

  class SuppressLogging implements Closeable {
    List<Logger> loggers = new ArrayList<>();
    List<Level> levels = new ArrayList<>();

    SuppressLogging(String... names) {
      for (String n : names) {
        Logger logger = Logger.getLogger(n);
        loggers.add(logger);
        levels.add(logger.getLevel());
        logger.setLevel(Level.OFF);
      }
    }

    @Override
    public void close() throws IOException {
      Iterator<Level> level = levels.iterator();
      for (Logger logger : loggers) {
        logger.setLevel(level.next());
      }
    }
  }
}

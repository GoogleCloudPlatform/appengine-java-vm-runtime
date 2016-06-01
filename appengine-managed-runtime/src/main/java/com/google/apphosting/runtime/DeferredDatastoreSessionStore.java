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

package com.google.apphosting.runtime;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskAgeLimitSeconds;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TransientFailureException;

import java.lang.reflect.Constructor;

/**
 * A {@link DatastoreSessionStore} extension that defers all datastore writes
 * via the taskqueue.
 *
 */
public class DeferredDatastoreSessionStore extends DatastoreSessionStore {

  /**
   * Try to save the session state for 10 seconds, then give up.
   */
  private static final int SAVE_TASK_AGE_LIMIT_SECS = 10;

  // The DeferredTask implementations we use to put and delete session data in
  // the datastore are are general-purpose, but we're not ready to expose them
  // in the public api, so we access them via reflection.
  private static final Constructor<DeferredTask> putDeferredTaskConstructor;
  private static final Constructor<DeferredTask> deleteDeferredTaskConstructor;

  static {
    putDeferredTaskConstructor =
        getConstructor(
            DeferredTask.class.getPackage().getName() + ".DatastorePutDeferredTask", Entity.class);
    deleteDeferredTaskConstructor =
        getConstructor(
            DeferredTask.class.getPackage().getName() + ".DatastoreDeleteDeferredTask", Key.class);
  }

  private final Queue queue;

  public DeferredDatastoreSessionStore(String queueName) {
    this.queue =
        queueName == null ? QueueFactory.getDefaultQueue() : QueueFactory.getQueue(queueName);
  }

  @Override
  public void saveSession(String key, SessionData data) throws Retryable {
    try {
      // Setting a timeout on retries to reduce the likelihood that session
      // state "reverts."  This can happen if a session in state s1 is saved
      // but the write fails.  Then the session in state s2 is saved and the
      // write succeeds.  Then a retry of the save of the session in s1
      // succeeds.  We could use version numbers in the session to detect this
      // scenario, but it doesn't seem worth it.
      // The length of this timeout has been chosen arbitrarily.  Maybe let
      // users set it?
      Entity entity = DatastoreSessionStore.createEntityForSession(key, data);
      queue.add(
          withPayload(newDeferredTask(putDeferredTaskConstructor, entity))
              .retryOptions(withTaskAgeLimitSeconds(SAVE_TASK_AGE_LIMIT_SECS)));
    } catch (TransientFailureException e) {
      throw new Retryable(e);
    }
  }

  @Override
  public void deleteSession(String keyStr) {
    Key key = DatastoreSessionStore.createKeyForSession(keyStr);
    // We'll let this task retry indefinitely.
    queue.add(withPayload(newDeferredTask(deleteDeferredTaskConstructor, key)));
  }

  /**
   * Helper method that returns a 1-arg constructor taking an arg of the given
   * type for the given class name
   */
  private static Constructor<DeferredTask> getConstructor(String clsName, Class<?> argType) {
    try {
      @SuppressWarnings("unchecked")
      Class<DeferredTask> cls = (Class<DeferredTask>) Class.forName(clsName);
      Constructor<DeferredTask> ctor = cls.getConstructor(argType);
      ctor.setAccessible(true);
      return ctor;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static DeferredTask newDeferredTask(Constructor<DeferredTask> ctor, Object arg) {
    try {
      return ctor.newInstance(arg);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

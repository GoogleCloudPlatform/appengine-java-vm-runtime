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

package com.google.apphosting.runtime;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskAgeLimitSeconds;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.apphosting.runtime.SessionData;

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

  private static final Constructor<DeferredTask> putDeferredTaskConstructor;
  private static final Constructor<DeferredTask> deleteDeferredTaskConstructor;

  static {
    putDeferredTaskConstructor = getConstructor(
        DeferredTask.class.getPackage().getName() + ".DatastorePutDeferredTask", Entity.class);
    deleteDeferredTaskConstructor = getConstructor(
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
      Entity e = createEntityForSession(key, data);
      queue.add(withPayload(newDeferredTask(putDeferredTaskConstructor, e))
          .retryOptions(withTaskAgeLimitSeconds(SAVE_TASK_AGE_LIMIT_SECS)));
    } catch (TransientFailureException e) {
      throw new Retryable(e);
    }
  }

  @Override
  public void deleteSession(String keyStr) {
    Key key = createKeyForSession(keyStr);
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

/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.apphosting.vmruntime;

import static com.google.appengine.repackaged.com.google.common.base.Preconditions.checkArgument;
import static com.google.appengine.repackaged.com.google.common.base.Preconditions.checkState;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread factory creating threads with a request specific thread local environment.
 *
 */
public class VmRequestThreadFactory implements ThreadFactory {
  private static final Logger logger =
      Logger.getLogger(VmRequestThreadFactory.class.getCanonicalName());

  private final Environment requestEnvironment;
  private final AtomicInteger newThreadEnter;
  private final AtomicInteger newThreadExit;
  private final Queue<Thread> createdThreads;
  private volatile boolean allowNewRequestThreadCreation;

  /**
   * Create a new VmRequestThreadFactory.
   *
   * @param requestEnvironment The request environment to install on each thread.
   */
  public VmRequestThreadFactory(Environment requestEnvironment) {
    this.requestEnvironment = requestEnvironment;
    this.newThreadEnter = new AtomicInteger();
    this.newThreadExit = new AtomicInteger();
    this.createdThreads = requestEnvironment == null ? null : new ConcurrentLinkedQueue<>();
    this.allowNewRequestThreadCreation = true;
  }

  /**
   * Create a new {@link Thread} that executes {@code runnable} for the duration of the current
   * request. This thread will be interrupted at the end of the current request.
   *
   * @param runnable The object whose run method is invoked when this thread is started. If null,
   *        this classes run method does nothing.
   *
   * @throws ApiProxy.ApiProxyException If called outside of a running request.
   * @throws IllegalStateException If called after the request thread stops.
   */
  @Override
  public Thread newThread(final Runnable runnable) {
    try {
      // Keep a count of enter and exits to this method, so that interruptRequestThreads
      // can observe that all calls that entered before a change to allowNewRequestThreadCreation
      // have exited.
      checkState(newThreadEnter.incrementAndGet() > 0, "Too many threads created by request");
      checkState(requestEnvironment != null,
          "Request threads can only be created within the context of a running request.");
      checkState(allowNewRequestThreadCreation,
          "Cannot create new threads after the request thread stops.");
      Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          if (runnable == null) {
            return;
          }
          checkState(allowNewRequestThreadCreation,
              "Cannot start new threads after the request thread stops.");
          ApiProxy.setEnvironmentForCurrentThread(requestEnvironment);
          runnable.run();
        }
      });
      createdThreads.add(thread);
      return thread;
    } finally {
      newThreadExit.incrementAndGet();
    }
  }

  /**
   * Returns an immutable copy of the current request thread list.
   */
  public List<Thread> getRequestThreads() {
    if (createdThreads == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(createdThreads));
  }

  /**
   * Interrupt all request threads created by the current request.
   */
  public void interruptRequestThreads() {
    allowNewRequestThreadCreation = false;
    // spin until all newThread calls will see state change
    int entered = newThreadEnter.get();
    while (newThreadExit.get() < entered) {
      Thread.yield();
    }
    // interrupt threads
    if (createdThreads != null) {
      for (Thread thread : createdThreads) {
        if (thread.isAlive()) {
          logger.warning(String.format("Request thread %s is still alive, forcing interrupt.",
              thread.getName()));
        }
        thread.interrupt();
      }
    }
  }

  /**
   * Waits at most {@code millis} milliseconds for all threads created by this factory to finish.
   *
   * @param millis The time to wait in milliseconds.
   *
   * @return True if all threads created by this factory joined successfully, false otherwise.
   *
   * @throws IllegalArgumentException if the value of {@code millis} is zero or negative
   */
  public boolean join(long millis) {
    checkArgument(millis >= 0, "Timeout value is negative.");
    long beDoneBy = System.currentTimeMillis() + millis;
    try {
      if (createdThreads != null) {
        for (Thread thread : createdThreads) {
          long waitTimeLeft = System.currentTimeMillis() - beDoneBy;
          if (waitTimeLeft <= 0) {
            return false;
          }
          thread.join(waitTimeLeft);
        }
      }
      return true;
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Interrupted while waiting for request threads to complete.", e);
    }
    return false;
  }
}

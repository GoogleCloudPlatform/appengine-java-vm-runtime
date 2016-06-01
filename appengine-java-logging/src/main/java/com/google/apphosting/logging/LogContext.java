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

package com.google.apphosting.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The context for logging information associated with the current Thread.
 * <p>
 * <p>This is an implementation of a Mapped Diagnostic Context for use with the java.util.logging
 * framework.
 */
public class LogContext extends ConcurrentHashMap<String, Object> {

  private static final ThreadLocal<LogContext> threadContext =
      new ThreadLocal<LogContext>() {
        @Override
        protected LogContext initialValue() {
          return new LogContext();
        }
      };

  private final Map<String, Object> values = new ConcurrentHashMap<>();

  private LogContext() {}

  /**
   * Returns the log context associated with the current Thread.
   */
  public static LogContext current() {
    return threadContext.get();
  }

  /**
   * Remove the log context associated with the current Thread.
   */
  public static void nullLogContext() {
    threadContext.set(null);
  }

  /**
   * Remove the log context associated with the current Thread.
   */
  public static void removeLogContext() {
    threadContext.remove();
  }

  /**
   * Returns the value of a context property.
   *
   * @param name the name of the property
   * @param type the type of value expected
   * @param <T>  the type of value expected
   * @return the property's value
   */
  public <T> T get(String name, Class<T> type) {
    return type.cast(values.get(name));
  }

  /**
   * Stream all property values defined in this context.
   */
  public Stream<Entry<String, Object>> stream() {
    return values.entrySet().stream();
  }
}

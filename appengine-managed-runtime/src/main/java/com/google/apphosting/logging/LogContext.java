/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
 */
package com.google.apphosting.logging;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * The context for logging information associated with the current Thread.
 * <p>
 * <p>This is an implementation of a Mapped Diagnostic Context for use with the java.util.logging
 * framework.
 */
public class LogContext {

    private static final ThreadLocal<LogContext> threadContext = new ThreadLocal<>();

    private final Map<String, Object> values;

    public LogContext(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Returns the log context associated with the current Thread.
     */
    public static LogContext current() {
        return threadContext.get();
    }

    /**
     * Execute a Runnable in this context. This context will be bound to the current Thread as the
     * Runnable is executing. It will automatically be unbound after execution.
     *
     * @param runnable the Runnable to be executed
     */
    public void execute(Runnable runnable) {
        LogContext parent = current();
        try {
            threadContext.set(this);
            runnable.run();
        } finally {
            if (parent == null) {
                threadContext.remove();
            } else {
                threadContext.set(parent);
            }
        }
    }

    /**
     * Execute a Callable in this context. This context will be bound to the current Thread as the
     * Runnable is executing. It will automatically be unbound after execution.
     *
     * @param callable the Callable to be executed
     */
    public <T> T execute(Callable<T> callable) throws Exception {
        LogContext parent = current();
        try {
            threadContext.set(this);
            return callable.call();
        } finally {
            if (parent == null) {
                threadContext.remove();
            } else {
                threadContext.set(parent);
            }
        }
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
    public Stream<Map.Entry<String, Object>> stream() {
        return values.entrySet().stream();
    }

    /**
     * Perform an action for each property in this context.
     */
    public void forEach(BiConsumer<String, Object> consumer) {
        stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }
}

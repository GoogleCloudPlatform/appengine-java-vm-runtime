package com.google.apphosting.vmruntime;/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.cloud.logging.GaeFlexLoggingEnhancer;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.handler.ContextHandler.ContextScopeListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Jetty {@link ContextScopeListener} that is called whenever
 * a container managed thread enters or exits the scope of a context and/or request.
 * Used to maintain {@link ThreadLocal} references to the current request and
 * Google traceID, primarily for logging.
 */
public class RequestContextScope implements ContextHandler.ContextScopeListener {
  static final Logger logger = Logger.getLogger(RequestContextScope.class.getName());

  private static final String X_CLOUD_TRACE = "x-cloud-trace-context";
  private static final ThreadLocal<Integer> contextDepth = new ThreadLocal<>();

  @Override
  public void enterScope(Context context, Request request, Object reason) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("enterScope " + context);
    }
    if (request != null) {
      Integer depth = contextDepth.get();
      if (depth == null || depth.intValue() == 0) {
        depth = 1;
        String traceId = (String) request.getAttribute(X_CLOUD_TRACE);
        if (traceId == null) {
          traceId = request.getHeader(X_CLOUD_TRACE);
          if (traceId != null) {
            int slash = traceId.indexOf('/');
            if (slash >= 0) {
              traceId = traceId.substring(0, slash);
            }
            request.setAttribute(X_CLOUD_TRACE, traceId);
            GaeFlexLoggingEnhancer.setCurrentTraceId(traceId);
          }
        } else {
          depth = depth + 1;
        }
        contextDepth.set(depth);
      }
    }
  }

  @Override
  public void exitScope(Context context, Request request) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("exitScope " + context);
    }
    Integer depth = contextDepth.get();
    if (depth != null) {
      if (depth > 1) {
        contextDepth.set(depth - 1);
      } else {
        contextDepth.remove();
        GaeFlexLoggingEnhancer.setCurrentTraceId(null);
      }
    }
  }
}
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

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionIdManager;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.component.ContainerLifeCycle;

import java.util.EventListener;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Does the bare minimum to create a NoOpSession. This session manager should be used only when
 * appengine-web.xml has disabled sessions. The purpose of it is to provide an implementation
 * with just enough compliance with the servlet session api so that frameworks that require the
 * session api will still work, albeit without the costs associated with distributed persistent
 * sessions (ie sessions enabled in appengine-web.xml).
 */
public class NoOpSessionManager extends ContainerLifeCycle implements SessionManager {

  private SessionIdManager idManager;
  private SessionHandler handler;
  private ServletContext context;

  @Override
  protected void doStart() throws Exception {
    context = ContextHandler.getCurrentContext();
    Server server = handler.getServer();

    synchronized (server) {
      if (idManager == null) {
        idManager = server.getSessionIdManager();
        if (idManager == null) {
          // Create the NoOpSessionIdManager and set it as the shared
          // SessionIdManager for the Server, being careful NOT to use
          // the webapp context's classloader, otherwise if the context
          // is restarted, the classloader is leaked.
          ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
          ClassLoader serverLoader = server.getClass().getClassLoader();
          try {
            Thread.currentThread().setContextClassLoader(serverLoader);
            idManager = new NoOpSessionIdManager();
            server.setSessionIdManager(idManager);
            server.manage(idManager);
            idManager.start();
          } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
          }
        }

        // server session id is never managed by this manager
        addBean(idManager, false);
      }
    }
    super.doStart();
  }

  @Override
  public HttpSession getHttpSession(String id) {
    return null;
  }

  @Override
  public HttpSession newHttpSession(HttpServletRequest request) {
    String id = idManager.newSessionId(request, System.currentTimeMillis());
    String nodeId = idManager.getNodeId(id, request);
    return new NoOpSession(id, nodeId, context);
  }

  @Override
  public boolean getHttpOnly() {
    return false;
  }

  @Override
  public int getMaxInactiveInterval() {
    return -1;
  }

  @Override
  public void setMaxInactiveInterval(int seconds) {}

  @Override
  public void setSessionHandler(SessionHandler handler) {
    this.handler = handler;
  }

  @Override
  public void addEventListener(EventListener listener) {}

  @Override
  public void removeEventListener(EventListener listener) {}

  @Override
  public void clearEventListeners() {}

  @Override
  public HttpCookie getSessionCookie(
      HttpSession session, String contextPath, boolean requestIsSecure) {
    return null;
  }

  @Override
  public SessionIdManager getSessionIdManager() {
    return idManager;
  }

  @Override
  public SessionIdManager getMetaManager() {
    return getSessionIdManager();
  }

  @Override
  public void setSessionIdManager(SessionIdManager idManager) {
    this.idManager = idManager;
  }

  @Override
  public boolean isValid(HttpSession session) {
    return ((NoOpSession) session).isValid();
  }

  @Override
  public String getNodeId(HttpSession session) {
    return ((NoOpSession) session).getNodeId();
  }

  @Override
  public String getClusterId(HttpSession session) {
    return ((NoOpSession) session).getId();
  }

  @Override
  public HttpCookie access(HttpSession session, boolean secure) {
    return null;
  }

  @Override
  public void complete(HttpSession session) {
    ((NoOpSession) session).complete();
  }

  @Override
  public void setSessionIdPathParameterName(String parameterName) {}

  @Override
  public String getSessionIdPathParameterName() {
    return null;
  }

  @Override
  public String getSessionIdPathParameterNamePrefix() {
    return null;
  }

  @Override
  public boolean isUsingCookies() {
    return false;
  }

  @Override
  public boolean isUsingURLs() {
    return false;
  }

  @Override
  public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
    return null;
  }

  @Override
  public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
    return null;
  }

  @Override
  public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {}

  @Override
  public SessionCookieConfig getSessionCookieConfig() {
    return null;
  }

  @Override
  public boolean isCheckingRemoteSessionIdEncoding() {
    return true;
  }

  @Override
  public void setCheckingRemoteSessionIdEncoding(boolean remote) {}

  @Override
  public void renewSessionId(
      String oldClusterId, String oldNodeId, String newClusterId, String newNodeId) {}
}

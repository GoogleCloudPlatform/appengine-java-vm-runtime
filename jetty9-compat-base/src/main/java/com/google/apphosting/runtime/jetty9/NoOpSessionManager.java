/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS-IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */


package com.google.apphosting.runtime.jetty9;

import java.util.EventListener;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionIdManager;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.component.ContainerLifeCycle;

/**
 * NoOpSessionManager
 *
 * Does the bare minimum to create a NoOpSession. This session manager should be used
 * only when app-cfg.xml has disabled sessions. The purpose of it is to provide an
 * implementation with just enough compliance with the servlet session api so that 
 * frameworks that require the session api will still work, albeit without the 
 * costs associated with distributed persistent sessions (ie sessions enabled in
 * app-cfg.xml).
 */
public class NoOpSessionManager  extends ContainerLifeCycle implements SessionManager {

  private SessionIdManager _idManager;
  private SessionHandler _handler;
  private ServletContext _context;

  /**
   * 
   */
  public NoOpSessionManager () {
  }


  @Override
  protected void doStart() throws Exception {
    _context=ContextHandler.getCurrentContext();
    final Server server = _handler.getServer();

    synchronized (server) {
      if (_idManager == null) {
        _idManager = server.getSessionIdManager();
        if (_idManager == null) {
          // Create the NoOpSessionIdManager and set it as the shared
          // SessionIdManager for the Server, being careful NOT to use
          // the webapp context's classloader, otherwise if the context
          // is restarted, the classloader is leaked.
          ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
          ClassLoader serverLoader = server.getClass().getClassLoader();
          try {
            Thread.currentThread().setContextClassLoader(serverLoader);
            _idManager = new NoOpSessionIdManager();
            server.setSessionIdManager(_idManager);
            server.manage(_idManager);
            _idManager.start();
          } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
          }
        }

        // server session id is never managed by this manager
        addBean(_idManager, false);
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

    String id =_idManager.newSessionId(request,System.currentTimeMillis());
    String nodeId =_idManager.getNodeId(id,request);
    return new NoOpSession(id, nodeId, _context);
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
  public void setMaxInactiveInterval(int seconds) {
  }

  @Override
  public void setSessionHandler(SessionHandler handler) {
    _handler = handler;
  }

  @Override
  public void addEventListener(EventListener listener) {
  }

  @Override
  public void removeEventListener(EventListener listener) {

  }

  @Override
  public void clearEventListeners() {
  }

  @Override
  public HttpCookie getSessionCookie(HttpSession session, String contextPath,
      boolean requestIsSecure) {
    return null;
  }

  @Override
  public SessionIdManager getSessionIdManager() {
    return _idManager;
  }

  @Override
  public SessionIdManager getMetaManager() {
    return getSessionIdManager();
  }

  @Override
  public void setSessionIdManager(SessionIdManager idManager) {
    _idManager = idManager;
  }

  @Override
  public boolean isValid(HttpSession session) {
    return ((NoOpSession)session).isValid();
  }

  @Override
  public String getNodeId(HttpSession session) {
    return ((NoOpSession)session).getNodeId();
  }

  @Override
  public String getClusterId(HttpSession session) {
    return ((NoOpSession)session).getId();
  }

  @Override
  public HttpCookie access(HttpSession session, boolean secure) {
    return null;
  }

  @Override
  public void complete(HttpSession session) {
    ((NoOpSession)session).complete();
  }

  @Override
  public void setSessionIdPathParameterName(String parameterName) {
  }

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
  public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
  }

  @Override
  public SessionCookieConfig getSessionCookieConfig() {
    return null;
  }

  @Override
  public boolean isCheckingRemoteSessionIdEncoding() {
    return true;
  }

  @Override
  public void setCheckingRemoteSessionIdEncoding(boolean remote) {
  }

  @Override
  public void renewSessionId(String oldClusterId, String oldNodeId, String newClusterId,
      String newNodeId) {
  }

}

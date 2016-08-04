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

package com.google.apphosting.vmruntime.jetty9;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.vmruntime.VmApiProxyEnvironment;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.URIUtil;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * {@code AppEngineAuthentication} is a utility class that can
 * configure a Jetty {@link SecurityHandler} to integrate with the App
 * Engine authentication model.
 *
 * <p>Specifically, it registers a custom {@link LoginAuthenticator}
 * instance that knows how to redirect users to a login URL using the
 * {@link UserService}, and a custom {@link UserIdentity} that is aware
 * of the custom roles provided by the App Engine.
 *
 */
class AppEngineAuthentication {
  private static final Logger log = Logger.getLogger(AppEngineAuthentication.class.getName());

  /**
   * URLs that begin with this prefix are reserved for internal use by
   * App Engine.  We assume that any URL with this prefix may be part
   * of an authentication flow (as in the Dev Appserver).
   */
  private static final String AUTH_URL_PREFIX = "/_ah/";

  private static final String AUTH_METHOD = "Google Login";

  private static final String REALM_NAME = "Google App Engine";

  // Keep in sync with com.google.apphosting.runtime.jetty.JettyServletEngineAdapter.
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  /**
   * Any authenticated user is a member of the {@code "*"} role, and
   * any administrators are members of the {@code "admin"} role. Any
   * other roles will be logged and ignored.
   */
  private static final String USER_ROLE = "*";
  private static final String ADMIN_ROLE = "admin";

  /**
   * Inject custom {@link LoginService} and {@link LoginAuthenticator}
   * implementations into the specified {@link ConstraintSecurityHandler}.
   */
  public static void configureSecurityHandler(
      ConstraintSecurityHandler handler, VmRuntimeTrustedAddressChecker checker) {

    LoginService loginService = new AppEngineLoginService();
    LoginAuthenticator authenticator = new AppEngineAuthenticator(checker);
    DefaultIdentityService identityService = new DefaultIdentityService();

    // Set allowed roles.
    handler.setRoles(new HashSet<String>(Arrays.asList(new String[] {USER_ROLE, ADMIN_ROLE})));
    handler.setLoginService(loginService);
    handler.setAuthenticator(authenticator);
    handler.setIdentityService(identityService);
    authenticator.setConfiguration(handler);
  }

  /**
   * {@code AppEngineAuthenticator} is a custom {@link LoginAuthenticator}
   * that knows how to redirect the current request to a login URL in
   * order to authenticate the user.
   */
  private static class AppEngineAuthenticator extends LoginAuthenticator {
    private VmRuntimeTrustedAddressChecker checker;

    /**
     * Checks if the request could to to the login page.
     *
     * @param uri The uri requested.
     *
     * @return True if the uri starts with "/_ah/", false otherwise.
     */
    private static boolean isLoginOrErrorPage(String uri) {
      return uri.indexOf(AUTH_URL_PREFIX) == 0;
    }

    private AppEngineAuthenticator(VmRuntimeTrustedAddressChecker checker) {
      this.checker = checker;
    }

    @Override
    public String getAuthMethod() {
      return AUTH_METHOD;
    }

    /**
     * Validate a response. Compare to:
     * j.c.g.apphosting.utils.jetty.AppEngineAuthentication.AppEngineAuthenticator.authenticate().
     *
     * <p>If authentication is required but the request comes from an untrusted ip, 307s the request
     * back to the trusted appserver.  Otherwise it will auth the request and return a login
     * url if needed.
     *
     * <p>From org.eclipse.jetty.server.Authentication:
     * @param servletRequest The request
     * @param servletResponse The response
     * @param mandatory True if authentication is mandatory.
     * @return An Authentication. If Authentication is successful, this will be a
     *         {@link org.eclipse.jetty.server.Authentication.User}. If a response has been sent by
     *         the Authenticator (which can be done for both successful and unsuccessful
     *         authentications), then the result will implement
     *         {@link org.eclipse.jetty.server.Authentication.ResponseSent}. If Authentication is
     *         not mandatory, then a {@link org.eclipse.jetty.server.Authentication.Deferred} may be
     *         returned.
     */
    @Override
    public Authentication validateRequest(
        ServletRequest servletRequest, ServletResponse servletResponse, boolean mandatory)
        throws ServerAuthException {
      HttpServletRequest request = (HttpServletRequest) servletRequest;
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      if (!mandatory) {
        return new DeferredAuthentication(this);
      }
      String remoteAddr = request.getHeader(VmApiProxyEnvironment.REAL_IP_HEADER);
      if (remoteAddr == null) {
        HttpChannel channel = ((Request) request).getHttpChannel();
        if (channel != null) {
          remoteAddr = channel.getEndPoint().getRemoteAddress().getAddress().getHostAddress();
        }
      }
      // Untrusted inbound ip for a login page, 307 the user to a server that we can trust.
      if (!checker.isTrustedRemoteAddr(remoteAddr)) {
        String redirectUrl =
            getThreadLocalEnvironment().getL7UnsafeRedirectUrl() + request.getRequestURI();
        if (request.getQueryString() != null) {
          redirectUrl += "?" + request.getQueryString();
        }
        response.setStatus(307);
        response.setHeader("Location", redirectUrl);
        return Authentication.SEND_CONTINUE;
      }
      // Trusted inbound ip, auth headers can be trusted.
      String uri = request.getRequestURI();
      if (uri == null) {
        uri = URIUtil.SLASH;
      }
      // Check this before checking if there is a user logged in, so
      // that we can log out properly.  Specifically, watch out for
      // the case where the user logs in, but as a role that isn't
      // allowed to see /*.  They should still be able to log out.
      if (isLoginOrErrorPage(uri) && !DeferredAuthentication.isDeferred(response)) {
        log.fine(
            "Got "
                + uri
                + ", returning DeferredAuthentication to "
                + "imply authentication is in progress.");
        return new DeferredAuthentication(this);
      }

      if (request.getAttribute(SKIP_ADMIN_CHECK_ATTR) != null) {
        log.fine("Returning DeferredAuthentication because of SkipAdminCheck.");
        // Warning: returning DeferredAuthentication here will bypass security restrictions!
        return new DeferredAuthentication(this);
      }

      if (response == null) {
        throw new ServerAuthException("validateRequest called with null response!!!");
      }

      try {
        UserService userService = UserServiceFactory.getUserService();
        // If the user is authenticated already, just create a
        // AppEnginePrincipal or AppEngineFederatedPrincipal for them.
        if (userService.isUserLoggedIn()) {
          UserIdentity user = _loginService.login(null, null, null);
          log.fine("authenticate() returning new principal for " + user);
          if (user != null) {
            return new UserAuthentication(getAuthMethod(), user);
          }
        }

        if (DeferredAuthentication.isDeferred(response)) {
          return Authentication.UNAUTHENTICATED;
        }

        try {
          log.fine("Got " + request.getRequestURI() + " but no one was logged in, redirecting.");
          String url = userService.createLoginURL(getFullURL(request));
          response.sendRedirect(url);
          // Tell Jetty that we've already committed a response here.
          return Authentication.SEND_CONTINUE;
        } catch (ApiProxy.ApiProxyException ex) {
          // If we couldn't get a login URL for some reason, return a 403 instead.
          log.log(Level.SEVERE, "Could not get login URL:", ex);
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
          return Authentication.SEND_FAILURE;
        }
      } catch (IOException ex) {
        log.log(Level.WARNING, "Got an IOException from sendRedirect:", ex);
        throw new ServerAuthException(ex);
      }
    }

    /*
     * We are not using sessions for authentication.
     */
    @Override
    protected HttpSession renewSession(HttpServletRequest request, HttpServletResponse response) {
      log.warning("renewSession throwing an UnsupportedOperationException");
      throw new UnsupportedOperationException();
    }

    /*
     * This seems to only be used by JaspiAuthenticator, all other Authenticators return true.
     */
    @Override
    public boolean secureResponse(
        ServletRequest servletRequest,
        ServletResponse servletResponse,
        boolean isAuthMandatory,
        Authentication.User user) {
      return true;
    }

    /**
     * Returns the thread local environment if it is a VmApiProxyEnvironment.
     *
     * @return The ThreadLocal environment or null if no VmApiProxyEnvironment is set.
     */
    private VmApiProxyEnvironment getThreadLocalEnvironment() {
      Environment env = ApiProxy.getCurrentEnvironment();
      if (env instanceof VmApiProxyEnvironment) {
        return (VmApiProxyEnvironment) env;
      }
      return null;
    }
  }

  /**
   * Returns the full URL of the specified request, including any query string.
   */
  private static String getFullURL(HttpServletRequest request) {
    StringBuffer buffer = request.getRequestURL();
    if (request.getQueryString() != null) {
      buffer.append('?');
      buffer.append(request.getQueryString());
    }
    return buffer.toString();
  }

  /**
   * {@code AppEngineLoginService} is a custom Jetty {@link LoginService} that is aware of the two
   * special role names implemented by Google App Engine. Any authenticated user is a member of the
   * {@code "*"} role, and any administrators are members of the {@code "admin"} role. Any other
   * roles will be logged and ignored.
   */
  private static class AppEngineLoginService implements LoginService {
    private IdentityService identityService;

    @Override
    public String getName() {
      return REALM_NAME;
    }

    @Override
    public UserIdentity login(
        String unusedUsername, Object unusedCredentials, ServletRequest request) {
      AppEngineUserIdentity appEngineUserIdentity = loadUser();
      return appEngineUserIdentity;
    }

    /**
     * Creates a new AppEngineUserIdentity based on information retrieved from the Users API.
     *
     * @return A AppEngineUserIdentity if a user is logged in, or null otherwise.
     */
    private AppEngineUserIdentity loadUser() {
      UserService userService = UserServiceFactory.getUserService();
      User engineUser = userService.getCurrentUser();
      if (engineUser == null) {
        return null;
      }
      return new AppEngineUserIdentity(new AppEnginePrincipal(engineUser));
    }

    @Override
    public IdentityService getIdentityService() {
      return identityService;
    }

    @Override
    public void logout(UserIdentity user) {
      // Jetty calls this on every request -- even if user is null!
      if (user != null) {
        log.fine("Ignoring logout call for: " + user);
      }
    }

    @Override
    public void setIdentityService(IdentityService identityService) {
      this.identityService = identityService;
    }

    @Override
    public boolean validate(UserIdentity user) {
      log.warning("validate(" + user + ") throwing UnsupportedOperationException.");
      throw new UnsupportedOperationException();
    }
  }

  /**
   * {@code AppEnginePrincipal} is an implementation of {@link Principal}
   * that represents a logged-in Google App Engine user.
   */
  public static class AppEnginePrincipal implements Principal {
    private final User user;

    public AppEnginePrincipal(User user) {
      this.user = user;
    }

    public User getUser() {
      return user;
    }

    @Override
    public String getName() {
      if ((user.getFederatedIdentity() != null) && (user.getFederatedIdentity().length() > 0)) {
        return user.getFederatedIdentity();
      }
      return user.getEmail();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof AppEnginePrincipal) {
        return user.equals(((AppEnginePrincipal) other).user);
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return user.toString();
    }

    @Override
    public int hashCode() {
      return user.hashCode();
    }
  }

  /**
   * {@code AppEngineUserIdentity} is an implementation of {@link UserIdentity}
   * that represents a logged-in Google App Engine user.
   */
  public static class AppEngineUserIdentity implements UserIdentity {

    private final AppEnginePrincipal userPrincipal;

    public AppEngineUserIdentity(AppEnginePrincipal userPrincipal) {
      this.userPrincipal = userPrincipal;
    }

    /*
     * Only used by jaas and jaspi.
     */
    @Override
    public Subject getSubject() {
      log.warning("getSubject() throwing UnsupportedOperationException.");
      throw new UnsupportedOperationException();
    }

    @Override
    public Principal getUserPrincipal() {
      return userPrincipal;
    }

    @Override
    public boolean isUserInRole(String role, Scope unusedScope) {
      UserService userService = UserServiceFactory.getUserService();
      log.fine("Checking if principal " + userPrincipal + " is in role " + role);
      if (userPrincipal == null) {
        log.fine("isUserInRole() called with null principal.");
        return false;
      }

      if (USER_ROLE.equals(role)) {
        return true;
      }

      if (ADMIN_ROLE.equals(role)) {
        User user = userPrincipal.getUser();
        if (user.equals(userService.getCurrentUser())) {
          return userService.isUserAdmin();
        } else {
          // TODO(user): I'm not sure this will happen in
          // practice. If it does, we may need to pass an
          // application's admin list down somehow.
          log.severe("Cannot tell if non-logged-in user " + user + " is an admin.");
          return false;
        }
      } else {
        log.warning("Unknown role: " + role + ".");
        return false;
      }
    }

    @Override
    public String toString() {
      return AppEngineUserIdentity.class.getSimpleName() + "('" + userPrincipal + "')";
    }
  }
}

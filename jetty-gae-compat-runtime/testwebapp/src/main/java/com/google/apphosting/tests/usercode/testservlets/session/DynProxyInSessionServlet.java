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

package com.google.apphosting.tests.usercode.testservlets.session;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * This test Servlet is used by testDynamicProxyInSession() in JavaRuntimeTest
 * to test that a dynamic proxy object can be serialized to and deserialized
 * from the HttpSession.
 * <p>
 * We test with three different dynamic proxies: One for a public interface from user code,
 * one for a public JDK interface, and one for a private interface from user code.
 */
public class DynProxyInSessionServlet extends HttpServlet {

  private static interface SecretKeeper extends Serializable {
    String getSecret();
  }

  private static class SecretKeeperImpl implements SecretKeeper {
    @Override
    public String getSecret() {
      return "secret";
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    boolean expectValueInSession = (req.getParameter("expectSession") != null);
    HttpSession session = req.getSession();
    if (expectValueInSession) {
      Foo foo = (Foo) session.getAttribute("foo");
      if (foo == null) {
        throw new RuntimeException("Did not find foo in the session.");
      }
      Iterable iterable = (Iterable) session.getAttribute("iterable");
      if (iterable == null) {
        throw new RuntimeException("Did not find iterable in the session.");
      }
      SecretKeeper secretKeeper = (SecretKeeper) session.getAttribute("secret");
      if (secretKeeper == null) {
        throw new RuntimeException("Did not find secretKeeper in the session.");
      }
    } else {
      Foo foo = (Foo) MyProxy.newInstance(new FooImpl());
      session.setAttribute("foo", foo);
      Iterable iterable = (Iterable) MyProxy.newInstance(new ArrayList(0));
      session.setAttribute("iterable", iterable);
      SecretKeeper secretKeeper = (SecretKeeper) MyProxy.newInstance(new SecretKeeperImpl());
      session.setAttribute("secret", secretKeeper);
    }
  }
}

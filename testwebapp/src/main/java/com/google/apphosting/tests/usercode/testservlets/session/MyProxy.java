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

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Used in {@link DynProxyInSessionServlet}.
 */
public class MyProxy implements InvocationHandler, Serializable {
  private static final long serialVersionUID = 1L;
  private Object target;

  public static Object newInstance(Object target) {
    return java.lang.reflect.Proxy.newProxyInstance(
        target.getClass().getClassLoader(), target.getClass().getInterfaces(), new MyProxy(target));
  }

  private MyProxy(Object obj) {
    this.target = obj;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return method.invoke(target, args);
  }
}

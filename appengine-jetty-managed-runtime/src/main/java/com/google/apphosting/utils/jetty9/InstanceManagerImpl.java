/**
 * Copyright 2012 Google Inc. All Rights Reserved.
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


package com.google.apphosting.utils.jetty9;

import org.apache.tomcat.InstanceManager;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

import javax.naming.NamingException;

/**
 * Instance manager class required by tomcat for JSP compilation under Jetty 9.
 *
 *  This class is installed into Jetty by setting an attribute on the servlet context. Specifically
 * the "org.apache.tomcat.InstanceManager" key has to map to an instance of a class implementing
 * InstanceManager (for example this class).
 *
 */
public class InstanceManagerImpl implements InstanceManager {

  private static final Logger logger =
      Logger.getLogger(InstanceManagerImpl.class.getName());

  public Object newInstance(String className)
      throws IllegalAccessException, InvocationTargetException, NamingException,
      InstantiationException, ClassNotFoundException {
    logger.warning(" newInstance(String className)=" + className);
    return newInstance(className, this.getClass().getClassLoader());
  }

  public Object newInstance(String fqcn, ClassLoader classLoader)
      throws IllegalAccessException, InvocationTargetException, NamingException,
      InstantiationException, ClassNotFoundException {
    logger.warning(" newInstance(String fqcn)=" + fqcn);
    Class<?> cl = classLoader.loadClass(fqcn);
    return cl.newInstance();
  }

  public Object newInstance(Class<?> clazz) throws IllegalAccessException,
      InvocationTargetException, NamingException, InstantiationException {
    logger.info("Object newInstance(Class<?> clazz) not implemented yet.");
    return null;
  }

  public void newInstance(Object o)
      throws IllegalAccessException, InvocationTargetException, NamingException {
    logger.warning(" newInstance(object =" + o);

  }

  public void destroyInstance(Object o)
      throws IllegalAccessException, InvocationTargetException {
    logger.warning(" destroyInstance(object =" + o);
  }
}

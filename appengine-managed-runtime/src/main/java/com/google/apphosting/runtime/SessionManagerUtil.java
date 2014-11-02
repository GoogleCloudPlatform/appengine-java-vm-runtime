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


package com.google.apphosting.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class SessionManagerUtil {

  public static byte[] serialize(Object value) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(value);
      return baos.toByteArray();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static Object deserialize(byte[] bytes) {
    ClassLoader classLoader =
        AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
          public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
          }
        });
    try {
      ObjectInputStream ois = new DelegatingObjectInputStream(
          new ByteArrayInputStream(bytes), classLoader);
      return ois.readObject();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    } catch (ClassNotFoundException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * {@code DelegatingObjectInputStream} is an {@link
   * ObjectInputStream} that uses the specified class loader to
   * deserialize objects rather than the classloader that loaded the
   * calling class.
   *
   * <p>One would think this would already be built into the JRE, but
   * according to
   * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4340158 fixing
   * this is such a low priority that the bug was simply closed.
   */
  public static class DelegatingObjectInputStream extends ObjectInputStream {

    private static final Map<String, Class> primitiveTypes = new HashMap<String, Class>(8, 1.0f);
    static {
      primitiveTypes.put("boolean", boolean.class);
      primitiveTypes.put("byte", byte.class);
      primitiveTypes.put("char", char.class);
      primitiveTypes.put("short", short.class);
      primitiveTypes.put("int", int.class);
      primitiveTypes.put("long", long.class);
      primitiveTypes.put("float", float.class);
      primitiveTypes.put("double", double.class);
      primitiveTypes.put("void", void.class);
    }

    private final ClassLoader classLoader;

    public DelegatingObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
      super(in);
      this.classLoader = classLoader;
    }

    @Override
    protected Class resolveClass(ObjectStreamClass classDesc)
        throws IOException, ClassNotFoundException {

      String name = classDesc.getName();
      Class c = primitiveTypes.get(name);
      if (c != null) {
        return c;
      }
      return Class.forName(classDesc.getName(), false, classLoader);
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
        throws IOException, ClassNotFoundException {
      ClassLoader nonPublicLoader = null;
      boolean hasNonPublicInterface = false;

      Class[] classObjs = new Class[interfaces.length];
      for (int i = 0; i < interfaces.length; i++) {
        Class cl = Class.forName(interfaces[i], false, classLoader);
        if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
          if (hasNonPublicInterface) {
            if (nonPublicLoader != cl.getClassLoader()) {
              throw new IllegalAccessError("conflicting non-public interface class loaders");
            }
          } else {
            nonPublicLoader = cl.getClassLoader();
            hasNonPublicInterface = true;
          }
        }
        classObjs[i] = cl;
      }
      try {
        return Proxy.getProxyClass(
            hasNonPublicInterface ? nonPublicLoader : classLoader, classObjs);
      } catch (IllegalArgumentException e) {
        throw new ClassNotFoundException(null, e);
      }
    }
  }
}

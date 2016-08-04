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

package com.google.apphosting.tests.usercode.testservlets.security;

import java.awt.Color;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Tests enforcement of the WhiteList.
 *
 */
public class TestWhiteList implements Runnable {
  public void run() {
    try {
      try {
        new Color(255, 255, 255);
        throw new RuntimeException("Expected a NoClassDefFoundError");
      } catch (NoClassDefFoundError e) {
        // good
      }

      // In production, this would return the stub class for Color. We return the
      // real Class in the dev_appserver, but don't allow you to do anything
      // substantive with it.
      Class.forName("java.awt.Color");
      Class<Color> colorClass = Color.class;

      Field whiteField = colorClass.getField("WHITE");

      try {
        whiteField.get(null);
        throw new RuntimeException("Expected to get an IllegalAccessException.");
      } catch (IllegalAccessException e) {
        // good
      }

      Constructor cons = colorClass.getConstructor(int.class);

      try {
        cons.newInstance(120);
        throw new RuntimeException("Expected an IllegalAccessException");
      } catch (IllegalAccessException e) {
        // good
      }

      try {
        Point.class.newInstance();
        throw new RuntimeException("Expected an IllegalAccessException");
      } catch (IllegalAccessException e) {
        // good
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

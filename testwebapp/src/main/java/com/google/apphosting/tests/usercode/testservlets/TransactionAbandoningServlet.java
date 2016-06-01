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

package com.google.apphosting.tests.usercode.testservlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that starts a transaction and never finishes it.
 *
 */
public class TransactionAbandoningServlet extends HttpServlet {

  // The test that invokes this servlet reaches in and grabs this value.
  public static String mostRecentTxnId = null;

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    mostRecentTxnId = null; // clear out any old values
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    mostRecentTxnId = datastore.beginTransaction().getId();
    if (datastore.getActiveTransactions().size() != 1) {
      throw new RuntimeException("Active transactions did not equal 1");
    }
    if (httpServletRequest.getParameter("throwExceptionBeforeReturn") != null) {
      throw new RuntimeException("boom");
    }
    httpServletResponse.setContentType("text/plain");
    httpServletResponse.getWriter().print(mostRecentTxnId);
  }
}

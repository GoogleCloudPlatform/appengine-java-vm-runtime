/**
 * Copyright 2008 Google Inc. All Rights Reserved.
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

package com.google.apphosting.utils.servlet;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;

/**
 * A servlet {@link Filter} that looks for datastore transactions that are
 * still active when request processing is finished.  The filter attempts
 * to roll back any transactions that are found, and swallows any exceptions
 * that are thrown while trying to perform roll backs.  This ensures that
 * any problems we encounter while trying to perform roll backs do not have any
 * impact on the result returned the user.
 *
 */
public class TransactionCleanupFilter implements Filter {

  private static final Logger logger = Logger.getLogger(TransactionCleanupFilter.class.getName());

  private DatastoreService datastoreService;

  public void init(FilterConfig filterConfig) {
    datastoreService = getDatastoreService();
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } finally {
      
      if (request.isAsyncStarted())
      {
        request.getAsyncContext().addListener(new AsyncListener() {
          @Override
          public void onTimeout(AsyncEvent arg0) throws IOException {
          }
          @Override
          public void onStartAsync(AsyncEvent arg0) throws IOException {
          }
          @Override
          public void onError(AsyncEvent arg0) throws IOException {
          }
          @Override
          public void onComplete(AsyncEvent arg0) throws IOException {
            Collection<Transaction> txns = datastoreService.getActiveTransactions();
            if (!txns.isEmpty()) {
              handleAbandonedTxns(txns);
            }
          }
        });
      }
      else
      {
        Collection<Transaction> txns = datastoreService.getActiveTransactions();
        if (!txns.isEmpty()) {
          handleAbandonedTxns(txns);
        }
      }
    }
  }

  void handleAbandonedTxns(Collection<Transaction> txns) {
    // TODO(user): In the dev appserver, capture a stack trace whenever a
    // transaction is started so we can print it here.
    for (Transaction txn : txns) {
      try {
        logger.warning("Request completed without committing or rolling back transaction with id "
            + txn.getId() + ".  Transaction will be rolled back.");
        txn.rollback();
      } catch (Exception e) {
        // We swallow exceptions so that there is no risk of our cleanup
        // impacting the actual result of the request.
        logger.log(Level.SEVERE, "Swallowing an exception we received while trying to rollback "
            + "abandoned transaction with id " + txn.getId(), e);
      }
    }
  }

  public void destroy() {
    datastoreService = null;
  }

  /**
   * Broken out to facilitate testing.
   */
  DatastoreService getDatastoreService() {
    return DatastoreServiceFactory.getDatastoreService();
  }
}

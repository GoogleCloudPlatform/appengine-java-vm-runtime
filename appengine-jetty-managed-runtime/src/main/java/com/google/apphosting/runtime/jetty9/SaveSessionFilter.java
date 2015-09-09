/**
 * Copyright 2011 Google Inc. All Rights Reserved.
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


package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.runtime.jetty9.SessionManager.AppEngineSession;

import java.io.IOException;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * {@code SaveSessionFilter} flushes a {@link AppEngineSession} to
 * persistent storage after each request completes.
 *
 */
public class SaveSessionFilter implements Filter {

  @Override
  public void init(FilterConfig config) {
    // No init.
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpReq = (HttpServletRequest) req;

    try {
      chain.doFilter(req, resp);
    } finally {
      HttpSession session = httpReq.getSession(false);
      if (session instanceof AppEngineSession) {
        final AppEngineSession aeSession = (AppEngineSession) session;
        
        if (req.isAsyncStarted())
        {
          req.getAsyncContext().addListener(new AsyncListener() {
            
            @Override
            public void onTimeout(AsyncEvent event) throws IOException {              
            }
            
            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {              
            }
            
            @Override
            public void onError(AsyncEvent event) throws IOException {  
            }
            
            @Override
            public void onComplete(AsyncEvent event) throws IOException {  
              if (aeSession.isDirty()) {
                aeSession.save();
              }            
            }
          });
        } else {
          if (aeSession.isDirty()) {
            aeSession.save();
          }
        }
      }
    }
  }

  @Override
  public void destroy() {
    // No destruction.
  }
}

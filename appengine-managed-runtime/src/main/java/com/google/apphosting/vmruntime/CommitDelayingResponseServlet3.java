/**
 * Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.apphosting.vmruntime;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

/**
 * Additions to {@link CommitDelayingResponse} for handling extra methods introduced in Servlet 3.0.
 *
 */
// This class requires that Servlet 3.1 (from //third_party/java/servlet/servlet_api/v3_1) is
// (from //third_party/java/servlet/servlet_api) and it does not contain the methods overridden
// here. This class will not compile with Servlet 2.5.
public class CommitDelayingResponseServlet3 extends CommitDelayingResponse {

  public CommitDelayingResponseServlet3(HttpServletResponse response) throws IOException {
    super(response);
  }

  @Override
  public String getHeader(String name) {
    if (name.equals(CONTENT_LENGTH)) {
      return output.hasContentLength() ? Long.toString(output.getContentLength()) : null;
    }
    return super.getHeader(name);
  }

  @Override
  public Collection<String> getHeaders(String name) {
    if (name.equals(CONTENT_LENGTH) && output.hasContentLength()) {
      return Arrays.asList(new String[] {Long.toString(output.getContentLength())});
    }
    return super.getHeaders(name);
  }

  @Override
  public Collection<String> getHeaderNames() {
    if (output.hasContentLength()) {
      // "Any changes to the returned Collection must not affect this HttpServletResponse."
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      builder.addAll(super.getHeaderNames());
      if (output.hasContentLength()) {
        builder.add(CONTENT_LENGTH);
      }
      return builder.build();
    }
    return super.getHeaderNames();
  }
}

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

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class AsyncServletIT extends VmRuntimeTestBase {

  /**
   * Test that blob upload requests are intercepted by the blob upload filter.
   */
  public void testAsyncPost() throws Exception {
    String postData = "Now is the winter of our discontent\n";

    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(300000);
    PostMethod post = new PostMethod(createUrl("/test-async").toString());
    post.addRequestHeader("Content-Type", "text/plain");
    post.setRequestEntity(
        new RequestEntity() {
          @Override
          public void writeRequest(OutputStream out) throws IOException {
            out.write(postData.getBytes(StandardCharsets.ISO_8859_1));
          }

          @Override
          public boolean isRepeatable() {
            return true;
          }

          @Override
          public String getContentType() {
            return "text/plain";
          }

          @Override
          public long getContentLength() {
            return postData.length();
          }
        });

    int httpCode = httpClient.executeMethod(post);
    assertThat(httpCode, equalTo(200));

    String body = post.getResponseBodyAsString();

    List<String> order = Arrays.asList(body.split(","));

    assertThat(order.get(0), startsWith("CONSTRUCT:"));
    String env0 = order.get(0).split(":")[1];

    assertThat(order.get(1), startsWith("INIT:"));
    assertThat(order.get(1), endsWith(env0));

    assertThat(order.get(2), startsWith("REQUEST:"));
    String env2 = order.get(2).split(":")[1];
    assertThat(env2, not(equalTo(env0)));

    assertThat(order.get(3), startsWith("ON_DATA_AVAILABLE:"));
    assertThat(order.get(3), endsWith(env2));

    assertThat(order.get(4), startsWith("ON_ALL_DATA_READ:"));
    assertThat(order.get(4), endsWith(env2));

    assertThat(order.get(5), startsWith("ASYNC:"));
    assertThat(order.get(5), endsWith(env2));

    assertThat(order.get(6), startsWith("STARTED:"));
    assertThat(order.get(6), endsWith(env2));

    assertThat(order.get(7), startsWith("ON_WRITE_POSSIBLE:"));
    assertThat(order.get(7), endsWith(env2));
  }
}

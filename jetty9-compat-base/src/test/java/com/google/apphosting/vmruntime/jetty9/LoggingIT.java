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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import com.google.gson.Gson;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LoggingIT extends VmRuntimeTestBase {

  public void testGet() throws Exception {

    //runner.dump();

    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    String query = "nano=" + Long.toHexString(System.nanoTime());
    GetMethod get = new GetMethod(createUrl("/testLogging?" + query).toString());
    int httpCode = httpClient.executeMethod(get);

    assertThat(httpCode, equalTo(200));

    String body = get.getResponseBodyAsString();
    assertThat(body, equalTo("FINE\nSEVERE\nfalse\n\n"));

    File log = runner.findLogFileThatContains(query);

    assertTrue(log.exists());

    // Look for the log entry with our query string
    try (FileInputStream in = new FileInputStream(log);
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        BufferedReader buf = new BufferedReader(reader)) {
      String line;
      while ((line = buf.readLine()) != null) {
        if (line.contains(query)) {
          break;
        }
      }

      JsonData data = new Gson().fromJson(line, JsonData.class);
      assertThat("Parsed Data", data, notNullValue());
      assertThat(data.severity, equalTo("INFO"));
      assertThat(data.message, org.hamcrest.Matchers.containsString("LogTest Hello " + query));

      line = buf.readLine();
      data = new Gson().fromJson(line, JsonData.class);
      assertThat("Parsed Data", data, notNullValue());
      assertThat(data.severity, equalTo("ERROR"));
      assertThat(data.message,
          org.hamcrest.Matchers.containsString("LoggingServlet doGet: not null"));

      line = buf.readLine();
      data = new Gson().fromJson(line, JsonData.class);
      assertThat("Parsed Data", data, notNullValue());
      assertThat(data.severity, equalTo("ERROR"));
      assertThat(data.message, org.hamcrest.Matchers.containsString("LoggingServlet doGet: null"));
    }
  }

  // Something that JSON can parser the JSON into
  public static class JsonData {
    public static class LogTimestamp {
      public long seconds;
      public long nanos;
    }


    public LogTimestamp timestamp;
    public String severity;
    public String thread;
    public String message;
    public String traceId;
  }
}

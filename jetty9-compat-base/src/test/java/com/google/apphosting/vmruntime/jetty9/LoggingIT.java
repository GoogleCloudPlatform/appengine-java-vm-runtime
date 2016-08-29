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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoggingIT extends VmRuntimeTestBase {

  /**
   * Find the log file that contains the specific content pattern.
   * <p>
   * This exists to get around the fact that the java.util.logging
   * layer will generate a new log file each time its reset/initialized
   * because it sees the lock from the previous instance before the
   * previous instance has a chance to close and remove the lock.
   * </p>
   * <p>
   * This function will iterate through all of the log files and
   * attempt to find the specific pattern you are interested in.
   * </p>
   *
   * @param regex the text (regex) pattern to look for in the log file.
   * @return the File for the discovered log file
   * @throws java.io.FileNotFoundException if unable to find a log
   * file with the specific text pattern
   * @throws IOException if unable to read a log file and/or pattern
   */
  public File findLogFileThatContains(File logDir, String regex) throws IOException {
    Pattern logPattern = Pattern.compile("log[0-9]+\\.[0-9]+\\.json");
    Pattern textPattern = Pattern.compile(regex);
    Matcher logMatcher;
    String line;

    for (File file : logDir.listFiles()) {
      logMatcher = logPattern.matcher(file.getName());
      boolean validFile = logMatcher.matches();
      if (validFile) {
        // search file
        try (FileInputStream in = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            BufferedReader buf = new BufferedReader(reader)) {
          Matcher textMatcher;
          while ((line = buf.readLine()) != null) {
            textMatcher = textPattern.matcher(line);
            if (textMatcher.find()) {
              return file;
            }
          }
        }
      }
    }

    throw new FileNotFoundException(
        "Unable to find pattern [" + textPattern.pattern() + "] in logs at " + logDir);
  }

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

    File log = findLogFileThatContains(runner.getLogDir(),query);

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

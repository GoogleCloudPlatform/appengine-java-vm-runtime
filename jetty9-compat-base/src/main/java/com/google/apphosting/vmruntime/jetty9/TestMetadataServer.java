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

import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.AFFINITY_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.APPENGINE_HOSTNAME_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.BACKEND_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.INSTANCE_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.PARTITION_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.PROJECT_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.USE_MVM_AGENT_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmApiProxyEnvironment.VERSION_ATTRIBUTE;
import static com.google.apphosting.vmruntime.VmMetadataCache.DEFAULT_META_DATA_SERVER;
import static com.google.apphosting.vmruntime.VmMetadataCache.META_DATA_PATTERN;

import org.eclipse.jetty.util.component.AbstractLifeCycle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal implementation of the metadata server running inside each VM.
 *
 */
public class TestMetadataServer extends AbstractLifeCycle implements Runnable {

  protected static final Logger logger = Logger.getLogger(TestMetadataServer.class.getName());

  private static final String PATH_PREFIX = "/computeMetadata/v1/instance/";
  public static final String PROJECT = "google.com:test-project";
  public static final String PARTITION = "testpartition";
  public static final String VERSION = "testversion";
  public static final String BACKEND = "testbackend";
  public static final String INSTANCE = "frontend1";
  public static final String AFFINITY = "true";
  public static final String APPENGINE_HOSTNAME = "testhostname";

  public static void main(String... args) throws Exception {
    TestMetadataServer test = new TestMetadataServer();
    test.start();
    Thread.sleep(5000);
    test.stop();
  }

  private ServerSocket serverSocket;
  private HashMap<String, String> responses = new HashMap<String, String>();
  private boolean run = true;

  public TestMetadataServer() {
    addMetadata("STOP", "STOP");
    addMetadata(PROJECT_ATTRIBUTE, PROJECT);
    addMetadata(PARTITION_ATTRIBUTE, PARTITION);
    addMetadata(BACKEND_ATTRIBUTE, BACKEND);
    addMetadata(VERSION_ATTRIBUTE, VERSION);
    addMetadata(INSTANCE_ATTRIBUTE, INSTANCE);
    addMetadata(AFFINITY_ATTRIBUTE, AFFINITY);
    addMetadata(APPENGINE_HOSTNAME_ATTRIBUTE, APPENGINE_HOSTNAME);
    addMetadata(USE_MVM_AGENT_ATTRIBUTE, Boolean.toString(false));
  }

  public void setUseMvm(boolean useMvm) {
    addMetadata(USE_MVM_AGENT_ATTRIBUTE, Boolean.toString(useMvm));
  }

  @Override
  public void doStart() throws Exception {
    serverSocket = new ServerSocket(0);
    int metadataPort = serverSocket.getLocalPort();
    System.setProperty("metadata_server", "127.0.0.1:" + metadataPort);
    logger.fine("Listening for metadata requests at port: " + metadataPort);

    Thread metadataThread = new Thread(this);
    metadataThread.setName("Metadata server");
    metadataThread.setDaemon(true);
    metadataThread.start();
  }

  @Override
  public void doStop() throws IOException {
    String path = "STOP";
    BufferedReader reader = null;
    HttpURLConnection connection = null;
    try {
      String server = System.getProperty("metadata_server", DEFAULT_META_DATA_SERVER);
      URL url = new URL(String.format(META_DATA_PATTERN, server, path));
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("Metadata-Flavor", "Google");

      connection.setConnectTimeout(120000);
      connection.setReadTimeout(120000);
      reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      StringBuffer result = new StringBuffer();
      char[] buffer = new char[4096];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        result.append(buffer, 0, read);
      }
      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
        return;
      }
      throw new IOException(
          "Meta-data request for '"
              + path
              + "' failed with error: "
              + connection.getResponseCode()
              + " "
              + connection.getResponseMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          logger.info("Error closing connection for " + path + ": " + e.getMessage());
        }
      }
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Adds a new metadata value to the server.
   *
   * @param path The path where the value is stored.
   * @param value The value to return.
   */
  public void addMetadata(String path, String value) {
    responses.put(PATH_PREFIX + path, value);
  }

  /**
   * Starts a single threaded metadata server.
   */
  @Override
  public void run() {
    try {
      while (run) {
        final Socket clientSocket = serverSocket.accept();
        BufferedWriter responseWriter = null;
        try {
          BufferedReader requestDataReader =
              new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
          responseWriter =
              new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
          String httpGetLine = requestDataReader.readLine();
          String[] getLineSplit = httpGetLine.split(" ");
          if (getLineSplit.length < 2) {
            responseWriter.write("HTTP/1.0 400 Bad Request\r\n\r\n");
            return;
          }
          // Seek to the end of the header.
          if (!verifyHeader(requestDataReader, "Metadata-Flavor: Google")) {
            responseWriter.write("HTTP/1.0 403 Access Denied\r\n\r\n");
            return;
          }
          // Check if we have content mapped to the requested path.
          String requestedPath = getLineSplit[1];
          String returnData = responses.get(requestedPath);
          if (returnData == null) {
            responseWriter.write("HTTP/1.0 404 Not Found\r\n\r\n");
            return;
          }
          responseWriter.write("HTTP/1.0 200 OK\r\n");
          responseWriter.write("Content-Type: text/plain\r\n\r\n");
          responseWriter.write(returnData + "\r\n");
          if (requestedPath.endsWith("/STOP")) {
            run = false;
          }
        } finally {
          if (responseWriter != null) {
            responseWriter.close();
          }
          clientSocket.close();
        }
      }
    } catch (IOException e) {
      logger.log(Level.WARNING, "Exception in TestMetadataServer: ", e);
    } finally {
      try {
        if (serverSocket != null) {
          serverSocket.close();
          logger.fine("CLOSING metadata requests at port: " + serverSocket.getLocalPort());
        }
      } catch (IOException e) {
        logger.log(Level.WARNING, "got Exception when closing the server socket.", e);
      }
    }
  }

  /**
   * Advance the buffer just past the next empty line, or to the end of the
   * buffer if no empty line exists.
   */
  private boolean verifyHeader(BufferedReader requestData, String header) throws IOException {
    boolean found = false;
    String line;
    do {
      line = requestData.readLine();
      if (line != null && header.equals(line.trim())) {
        found = true;
      }
    } while (line != null && line.trim().length() > 0);
    return found;
  }
}

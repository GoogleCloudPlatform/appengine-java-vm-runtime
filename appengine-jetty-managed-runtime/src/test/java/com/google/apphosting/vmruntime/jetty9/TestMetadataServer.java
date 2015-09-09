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

package com.google.apphosting.vmruntime.jetty9;

import static com.google.apphosting.vmruntime.jetty9.VmRuntimeTestBase.logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * Minimal implementation of the metadata server running inside each VM.
 *
 */
public class TestMetadataServer implements Runnable {

  private static final String PATH_PREFIX = "/computeMetadata/v1/instance/";
  private final int metadataPort;
  private HashMap<String, String> responses = new HashMap<String, String>();
  private boolean run = true;

  /**
   * Constructor.
   *
   * @param metadataPort The port the server should bind to.
   */
  public TestMetadataServer(int metadataPort) {
    this.metadataPort = metadataPort;
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
    ServerSocket serverSocket = null;
    try {
      logger.fine("TRYING TO Listen for metadata requests at port: " + metadataPort);
      serverSocket = new ServerSocket(metadataPort);
      logger.fine("Listening for metadata requests at port: " + metadataPort);
      while (run) {
        final Socket clientSocket = serverSocket.accept();
        BufferedWriter responseWriter = null;
        try {
          BufferedReader requestDataReader
                  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
          responseWriter
                  = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
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
          logger.fine("CLOSING metadata requests at port: " + metadataPort);

        }
      } catch (IOException e) {
        logger.log(Level.WARNING, "got Exception when closing the server socket.", e);
      }
    }
  }

  /**
   * Advance the buffer just past the next empty line, or to the end of the
   * buffer if no empty line exists.
   *
   * @param requestDataReader
   * @throws IOException
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

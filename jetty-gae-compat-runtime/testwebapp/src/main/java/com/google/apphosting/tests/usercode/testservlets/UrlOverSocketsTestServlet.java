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

import com.google.appengine.api.socket.SocketServicePb.AcceptReply;
import com.google.appengine.api.socket.SocketServicePb.AddressPort;
import com.google.appengine.api.socket.SocketServicePb.CloseReply;
import com.google.appengine.api.socket.SocketServicePb.ConnectReply;
import com.google.appengine.api.socket.SocketServicePb.CreateSocketReply;
import com.google.appengine.api.socket.SocketServicePb.GetSocketNameReply;
import com.google.appengine.api.socket.SocketServicePb.ListenReply;
import com.google.appengine.api.socket.SocketServicePb.ReceiveReply;
import com.google.appengine.api.socket.SocketServicePb.ReceiveRequest;
import com.google.appengine.api.socket.SocketServicePb.SendReply;
import com.google.appengine.api.socket.SocketServicePb.SendRequest;
import com.google.appengine.api.socket.SocketServicePb.SetSocketOptionsReply;
import com.google.appengine.api.socket.SocketServicePb.ShutDownReply;
import com.google.apphosting.api.ApiProxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class UrlOverSocketsTestServlet extends HttpServletTest {
  /**
   * Set up a mock delegate to handle resolve calls.
   */
  private ApiProxy.Delegate setUpMockDelegate() {
    ApiProxy.Delegate oldDelegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(new UrlMockDelegate());
    return oldDelegate;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/plain");
    ApiProxy.Delegate oldDelegate = setUpMockDelegate();
    try {
      testHttpUrl(response);
    } catch (AssertionFailedException e) {
      response.getWriter().print("Assertion failure - exception " + e.getMessage());
      e.printStackTrace(response.getWriter());
      return;
    } catch (Exception e) {
      response.getWriter().print("Failed - exception " + e.getMessage());
      e.printStackTrace(response.getWriter());
      return;
    } finally {
      ApiProxy.setDelegate(oldDelegate);
    }
    response.getWriter().print("Success!");
  }

  private void testHttpUrl(HttpServletResponse response)
      throws UnknownHostException, IOException, AssertionFailedException {
    URL url = new URL("http://10.1.1.1/hello.html");
    String payload = "aaaaaaaaaa";
    String httpResponse = "HTTP/1.0 200 OK\r\n\r\n" + payload;
    UrlMockDelegate.inputStream = new ByteArrayInputStream(httpResponse.getBytes("UTF-8"));
    String data = streamToString(url.openStream());
    assertEquals("URL http read", payload, new String(data), response);
  }

  private static String streamToString(InputStream istream) {
    Scanner scanner = new Scanner(istream);
    try {
      return scanner.next("[^\\000]*");
    } finally {
      scanner.close();
    }
  }

  static class UrlMockDelegate extends TestSocketServlet.MockDelegate {
    static InputStream inputStream;

    interface Responder {
      byte[] makeResponse(UrlMethod method, byte[] request);
    }

    public enum UrlMethod {
      CreateSocket(
          new Responder() {
            int descriptorCount = 0;

            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              CreateSocketReply response = new CreateSocketReply();
              response.setSocketDescriptor("mock-descriptor:" + ++descriptorCount);
              return response.toByteArray();
            }
          }),
      Connect(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new ConnectReply().toByteArray();
            }
          }),
      Listen(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new ListenReply().toByteArray();
            }
          }),
      Accept(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new AcceptReply().toByteArray();
            }
          }),
      Send(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              SendRequest requestProto = new SendRequest();
              requestProto.parseFrom(request);
              return new SendReply()
                  .setDataSent(requestProto.getDataAsBytes().length)
                  .toByteArray();
            }
          }),
      Receive(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              ReceiveRequest requestProto = new ReceiveRequest();
              requestProto.parseFrom(request);
              ReceiveReply response = new ReceiveReply();
              byte[] data = new byte[requestProto.getDataSize()];
              int size;
              try {
                size = inputStream.read(data);
              } catch (IOException e) {
                throw new RuntimeException("Unexpected exception trying to read from inputStream");
              }
              if (size < 0) {
                // Empty read..
                return response.toByteArray();
              }
              response.setDataAsBytes(Arrays.copyOf(data, size));
              return response.toByteArray();
            }
          }),
      Close(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new CloseReply().toByteArray();
            }
          }),
      ShutDown(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new ShutDownReply().toByteArray();
            }
          }),
      SetSocketOptions(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              return new SetSocketOptionsReply().toByteArray();
            }
          }),
      GetSocketName(
          new Responder() {
            @Override
            public byte[] makeResponse(UrlMethod method, byte[] request) {
              GetSocketNameReply reply = new GetSocketNameReply();
              AddressPort externalIp = reply.getMutableProxyExternalIp();
              externalIp.setPort(551212);
              externalIp.setPackedAddressAsBytes(new byte[] {127, 77, 88, 99});
              return reply.toByteArray();
            }
          });

      Responder responder;

      UrlMethod(Responder responder) {
        this.responder = responder;
      }

      /**
       * Make a response for this method.
       */
      public byte[] makeResponse(byte[] request) {
        return responder.makeResponse(this, request);
      }
    }

    /**
     * Returns a response for a given method and request.
     */
    @Override
    byte[] makeResponse(String methodName, byte[] request) {
      UrlMethod method = UrlMethod.valueOf(UrlMethod.class, methodName);
      if (method == null) {
        throw new UnsupportedOperationException();
      }
      return method.makeResponse(request);
    }
  }
}

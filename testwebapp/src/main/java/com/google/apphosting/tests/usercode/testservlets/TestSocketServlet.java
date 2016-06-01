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
import com.google.appengine.api.socket.SocketServicePb.SetSocketOptionsReply;
import com.google.appengine.api.socket.SocketServicePb.ShutDownReply;
import com.google.apphosting.api.ApiProxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class TestSocketServlet extends HttpServletTest {
  /**
   * Set up a mock delegate to handle resolve calls.
   */
  private ApiProxy.Delegate setUpMockDelegate() {
    ApiProxy.Delegate oldDelegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(new MockDelegate());
    return oldDelegate;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    ApiProxy.Delegate oldDelegate = setUpMockDelegate();

    response.setContentType("text/plain");
    try {
      testConnectWriteAndRead(response);
      testTimedConnect(response);
      testSocketOpt(response);
      testSetSocketImpl(response);
      testShutDownAndClose(response);
      testProxyConstructor(response);
      testSocketImplConstructor(response);
    } catch (AssertionFailedException e) {
      return;
      //return the error response
    } finally {
      ApiProxy.setDelegate(oldDelegate);
    }
    response.getWriter().print("Success!");
  }

  private void testShutDownAndClose(HttpServletResponse response)
      throws UnknownHostException, IOException {
    Socket socket = new Socket("10.1.1.1", 80);
    socket.shutdownInput();
    socket.shutdownOutput();
    socket.close();
  }

  private void testSetSocketImpl(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    SocketImplFactory mockFactory =
        new SocketImplFactory() {
          @Override
          public SocketImpl createSocketImpl() {
            return null;
          }
        };

    SocketException caught = null;
    try {
      Socket.setSocketImplFactory(mockFactory);
    } catch (SocketException e) {
      caught = e;
    }
    assertNotNull("caught", caught, response);
  }

  private void testSocketOpt(HttpServletResponse response)
      throws UnknownHostException, IOException {
    Socket socket = new Socket("10.1.1.1", 80);
    socket.setSoTimeout(10);
  }

  private byte[] readSomeData(Socket socket, int size) throws IOException {
    byte[] data = new byte[10];
    socket.getInputStream().read(data);
    return data;
  }

  private void testTimedConnect(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(InetAddress.getByName("10.1.1.1"), 80), 10);
    byte[] data = readSomeData(socket, 10);
    assertEquals("socket read", "aaaaaaaaaa", new String(data), response);
  }

  private void testConnectWriteAndRead(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    Socket socket = new Socket("10.1.1.1", 80);
    byte[] data = readSomeData(socket, 10);
    assertEquals("socket read", "aaaaaaaaaa", new String(data), response);
    socket.getOutputStream().write(data);
  }

  private void testProxyConstructor(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    Proxy proxy =
        new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(InetAddress.getByName("10.1.1.1"), 80));
    try {
      Socket socket = new Socket(proxy);
      assertTrue("Expected SecurityException when creating SOCKS socket.", false, response);
    } catch (SecurityException e) {
      // OK
    }

    Socket socket = new Socket(Proxy.NO_PROXY);
    socket.connect(new InetSocketAddress(InetAddress.getByName("10.1.1.1"), 80), 10);
  }

  private void testSocketImplConstructor(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    MockSocketImpl mockImpl = new MockSocketImpl();
    Socket socket = new Socket(mockImpl) {}; // Accessing protected constructor.

    try {
      socket.connect(new InetSocketAddress(InetAddress.getByName("10.1.1.1"), 80), 10);
      assertTrue(
          "Expected SecurityException when connecting with a non App Engine SocketImpl.",
          false,
          response);
    } catch (SecurityException e) {
      // OK
    }
  }

  /**
   * A mock ApiProxy.Delegate specifically for handling Socket calls.
   */
  static class MockDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    interface Responder {
      byte[] makeResponse(Method method, byte[] request);
    }

    public enum Method {
      CreateSocket(
          new Responder() {
            int descriptorCount = 0;

            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              CreateSocketReply response = new CreateSocketReply();
              response.setSocketDescriptor("mock-descriptor:" + ++descriptorCount);
              return response.toByteArray();
            }
          }),
      Connect(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new ConnectReply().toByteArray();
            }
          }),
      Listen(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new ListenReply().toByteArray();
            }
          }),
      Accept(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new AcceptReply().toByteArray();
            }
          }),
      Send(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new SendReply().toByteArray();
            }
          }),
      Receive(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              ReceiveRequest requestProto = new ReceiveRequest();
              requestProto.parseFrom(request);
              ReceiveReply response = new ReceiveReply();
              byte[] data = new byte[requestProto.getDataSize()];
              Arrays.fill(data, (byte) 'a');
              response.setDataAsBytes(data);
              return response.toByteArray();
            }
          }),
      Close(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new CloseReply().toByteArray();
            }
          }),
      ShutDown(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new ShutDownReply().toByteArray();
            }
          }),
      SetSocketOptions(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new SetSocketOptionsReply().toByteArray();
            }
          }),
      GetSocketName(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              GetSocketNameReply reply = new GetSocketNameReply();
              AddressPort externalIp = reply.getMutableProxyExternalIp();
              externalIp.setPort(551212);
              externalIp.setPackedAddressAsBytes(new byte[] {127, 77, 88, 99});
              return reply.toByteArray();
            }
          });

      Responder responder;

      Method(Responder responder) {
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
    byte[] makeResponse(String methodName, byte[] request) {
      Method method = Method.valueOf(Method.class, methodName);
      if (method == null) {
        throw new UnsupportedOperationException();
      }
      return method.makeResponse(request);
    }

    @Override
    public byte[] makeSyncCall(
        ApiProxy.Environment environment, String packageName, String methodName, byte[] request) {
      if (!"remote_socket".equals(packageName)) {
        throw new UnsupportedOperationException();
      }
      return makeResponse(methodName, request);
    }

    @Override
    public Future<byte[]> makeAsyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      if ("remote_socket".equals(packageName)) {
        final byte[] response = makeResponse(methodName, request);
        return new Future<byte[]>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public boolean isDone() {
            return true;
          }

          @Override
          public byte[] get() throws InterruptedException, ExecutionException {
            return response;
          }

          @Override
          public byte[] get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            return response;
          }
        };
      }
      throw new UnsupportedOperationException();
    }

    @Override
    public void log(ApiProxy.Environment environment, ApiProxy.LogRecord record) {}

    @Override
    public void flushLogs(ApiProxy.Environment environment) {}

    @Override
    public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
      return null;
    }
  }

  static class MockSocketImpl extends SocketImpl {
    @Override
    public void setOption(int optId, Object value) throws SocketException {}

    @Override
    public Object getOption(int optId) throws SocketException {
      return null;
    }

    @Override
    protected void create(boolean stream) throws IOException {}

    @Override
    protected void connect(String host, int port) throws IOException {}

    @Override
    protected void connect(InetAddress address, int port) throws IOException {}

    @Override
    protected void connect(SocketAddress address, int timeout) throws IOException {}

    @Override
    protected void bind(InetAddress host, int port) throws IOException {}

    @Override
    protected void listen(int backlog) throws IOException {}

    @Override
    protected void accept(SocketImpl s) throws IOException {}

    @Override
    protected InputStream getInputStream() throws IOException {
      return null;
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
      return null;
    }

    @Override
    protected int available() throws IOException {
      return 0;
    }

    @Override
    protected void close() throws IOException {}

    @Override
    protected void sendUrgentData(int data) throws IOException {}
  }
}

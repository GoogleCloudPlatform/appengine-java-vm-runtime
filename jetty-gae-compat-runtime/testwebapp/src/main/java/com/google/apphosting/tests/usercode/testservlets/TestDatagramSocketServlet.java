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
import com.google.appengine.api.socket.SocketServicePb.BindReply;
import com.google.appengine.api.socket.SocketServicePb.CloseReply;
import com.google.appengine.api.socket.SocketServicePb.ConnectReply;
import com.google.appengine.api.socket.SocketServicePb.CreateSocketReply;
import com.google.appengine.api.socket.SocketServicePb.GetSocketNameReply;
import com.google.appengine.api.socket.SocketServicePb.ListenReply;
import com.google.appengine.api.socket.SocketServicePb.ReceiveReply;
import com.google.appengine.api.socket.SocketServicePb.ReceiveRequest;
import com.google.appengine.api.socket.SocketServicePb.RemoteSocketServiceError;
import com.google.appengine.api.socket.SocketServicePb.SendReply;
import com.google.appengine.api.socket.SocketServicePb.SendRequest;
import com.google.appengine.api.socket.SocketServicePb.SetSocketOptionsReply;
import com.google.appengine.api.socket.SocketServicePb.ShutDownReply;
import com.google.apphosting.api.ApiProxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.DatagramSocketImplFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
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
public class TestDatagramSocketServlet extends HttpServletTest {
  private static final String SEND_STRING = "ssssssssss";
  private static final String READ_STRING = "rrrrrrrrrr";

  /**
   * Set up a mock delegate to socket resolve calls.
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
      testOpenAndClose(response);

      testConnectWriteAndRead(response);
      testSocketOpt(response);
      testSetDatagramSocketImpl(response);
      testSocketImplConstructor(response);
    } catch (AssertionFailedException e) {
      return;
      //return the error response
    } finally {
      ApiProxy.setDelegate(oldDelegate);
    }
    response.getWriter().print("Success!");
  }

  private void testOpenAndClose(HttpServletResponse response) throws IOException {
    DatagramSocket socket = new DatagramSocket(0);
    socket.close();
  }

  private void testSetDatagramSocketImpl(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    DatagramSocketImplFactory mockFactory =
        new DatagramSocketImplFactory() {
          @Override
          public DatagramSocketImpl createDatagramSocketImpl() {
            return null;
          }
        };

    SocketException caught = null;
    try {
      DatagramSocket.setDatagramSocketImplFactory(mockFactory);
    } catch (SocketException e) {
      caught = e;
    }
    assertNotNull("caught", caught, response);
  }

  private void testSocketOpt(HttpServletResponse response) throws IOException {
    DatagramSocket socket = new DatagramSocket(0);
    socket.setSoTimeout(10);
  }

  private DatagramPacket readSomeData(DatagramSocket socket, int size) throws IOException {
    byte[] data = new byte[size];
    DatagramPacket packet = new DatagramPacket(data, data.length);
    socket.receive(packet);
    return packet;
  }

  private void testConnectWriteAndRead(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    DatagramSocket socket = new DatagramSocket(9999);
    DatagramPacket packet =
        new DatagramPacket(SEND_STRING.getBytes(), 0, 10, InetAddress.getByName("10.1.1.1"), 9999);
    socket.send(packet);
    packet = readSomeData(socket, 10);
    assertEquals("socket packet read data", READ_STRING, new String(packet.getData()), response);
    assertEquals("socket packet port", 9999, packet.getPort(), response);
    assertEquals(
        "socket packet address", InetAddress.getByName("10.1.1.1"), packet.getAddress(), response);
  }

  private void testSocketImplConstructor(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    MockSocketImpl mockImpl = new MockSocketImpl();
    DatagramSocket socket = new DatagramSocket(mockImpl) {}; // Accessing protected constructor.

    try {
      socket.connect(InetAddress.getByName("10.1.1.1"), 9999);
      assertTrue(
          "Expected SecurityException when not using AppEngineDatagramSocketImpl.",
          false,
          response);
    } catch (SecurityException e) {
      // OK
    }
  }

  /**
   * A mock ApiProxy.Delegate specifically for handling Socket calls.
   */
  private static class MockDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    private static final String DESCRIPTOR_PREFIX = "mock-descriptor:";

    interface Responder {
      byte[] makeResponse(Method method, byte[] request);
    }

    public enum Method {
      Bind(
          new Responder() {
            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              return new BindReply().toByteArray();
            }
          }),
      CreateSocket(
          new Responder() {
            int descriptorCount = 0;

            @Override
            public byte[] makeResponse(Method method, byte[] request) {
              CreateSocketReply response = new CreateSocketReply();
              response.setSocketDescriptor(DESCRIPTOR_PREFIX + ++descriptorCount);
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
              SendRequest requestPb = new SendRequest();
              requestPb.parseFrom(request);
              if (!requestPb.getSocketDescriptor().startsWith(DESCRIPTOR_PREFIX)) {
                throw new ApiProxy.ApplicationException(
                    RemoteSocketServiceError.ErrorCode.SYSTEM_ERROR.getValue(),
                    "Descriptor not set correctly.");
              }
              if (!new String(requestPb.getDataAsBytes()).equals(SEND_STRING)) {
                throw new ApiProxy.ApplicationException(
                    RemoteSocketServiceError.ErrorCode.SYSTEM_ERROR.getValue(),
                    "Incorrect data being sent.");
              }
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
              response.setDataAsBytes(READ_STRING.getBytes());
              AddressPort addressPort = new AddressPort();
              addressPort.setPort(9999);
              addressPort.setPackedAddressAsBytes(
                  new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 10, 1, 1, 1});
              response.setReceivedFrom(addressPort);
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
    private byte[] makeResponse(String methodName, byte[] request) {
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

  private static class MockSocketImpl extends DatagramSocketImpl {
    @Override
    public void setOption(int optId, Object value) throws SocketException {}

    @Override
    public Object getOption(int optId) throws SocketException {
      return null;
    }

    @Override
    protected void create() throws SocketException {}

    @Override
    protected void bind(int lport, InetAddress laddr) throws SocketException {}

    @Override
    protected void send(DatagramPacket p) throws IOException {}

    @Override
    protected int peek(InetAddress i) throws IOException {
      return 0;
    }

    @Override
    protected int peekData(DatagramPacket p) throws IOException {
      return 0;
    }

    @Override
    protected void receive(DatagramPacket p) throws IOException {}

    @Override
    protected void setTTL(byte ttl) throws IOException {}

    @Override
    protected byte getTTL() throws IOException {
      return 0;
    }

    @Override
    protected void setTimeToLive(int ttl) throws IOException {}

    @Override
    protected int getTimeToLive() throws IOException {
      return 0;
    }

    @Override
    protected void join(InetAddress inetaddr) throws IOException {}

    @Override
    protected void leave(InetAddress inetaddr) throws IOException {}

    @Override
    protected void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {}

    @Override
    protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {}

    @Override
    protected void close() {}
  }
}

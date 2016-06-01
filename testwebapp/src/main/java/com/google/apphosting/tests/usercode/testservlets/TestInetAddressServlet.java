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

import com.google.appengine.api.socket.SocketServicePb.ResolveReply;
import com.google.apphosting.api.ApiProxy;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests the InetAddress, Inet4Address and Inet6Address mirrors.
 * Also tests the NetworkInterface mirror.
 */
public class TestInetAddressServlet extends HttpServletTest {
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
      testGetHostNameFirst(response);
      testGetLocalHost(response);
      testGetByAddress4(response);
      testGetByNameAndAddress4(response);
      testGetByAddress6(response);
      testGetByName(response);
      testNetworkInterface(response);
    } catch (AssertionFailedException e) {
      response.getWriter().print("\n\nAssertion stack trace: ");
      e.printStackTrace(response.getWriter());
      return;
      //return the error response
    } catch (Exception e) {
      response.getWriter().print("\nTest threw exception : ");
      e.printStackTrace(response.getWriter());
      return;
    } finally {
      ApiProxy.setDelegate(oldDelegate);
    }
    response.getWriter().print("Success!");
  }

  /**
   * If InetAddress.getHostName() was invoked before other methods on
   * InetAddress were ever invoked in the JVM, an illegal open() syscall
   * occurred. For this test to be valid it must come first.
   */
  private static void testGetHostNameFirst(HttpServletResponse response) throws Exception {
    InetAddress inetAddr = InetAddress.getByAddress(null, new byte[] {74, 125, (byte) 224, 50});
    // Test that no illegal syscalls occur. This indicates that our diverter has
    // been successfully installed and the network layer is passing through
    // to our mock API delegate instead of using native DNS.
    String hostName = inetAddr.getHostName();
    assertEquals("hostName", "74.125.224.50", hostName, response);
  }

  /**
   * Get instances of InetAddress by calling InetAddress.getLocalHost(),
   * InetAddres4.getLocalHost() and Inet6Address.getLocalHost() and then test
   * their properties.
   * @param response HttpServletResponse used to return failure message
   */
  private static void testGetLocalHost(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    InetAddress localHost = InetAddress.getLocalHost();
    testLocalHost(localHost, response);
    testLocalHost4((Inet4Address) localHost, response);
    localHost = Inet4Address.getLocalHost();
    testLocalHost(localHost, response);
    localHost = Inet6Address.getLocalHost();
    testLocalHost(localHost, response);
  }

  /**
   * Test properties of an instance of InetAddress obtained via getLocalHost().
   */
  private static void testLocalHost(InetAddress localHost, HttpServletResponse response)
      throws IOException, AssertionFailedException {
    assertNotNull("localhost", localHost, response);
    assertTrue("instanceof Inet4Addr", localHost instanceof Inet4Address, response);

    //getAddress
    byte[] bytes = localHost.getAddress();
    assertNotNull("localhost address-bytes", bytes, response);
    assertEquals("localhost address bytes.length", 4, bytes.length, response);
    assertEquals("first byte of localhost address", 127, bytes[0], response);
    assertEquals("last byte of localhost address", 1, bytes[3], response);

    String name = localHost.getCanonicalHostName();
    assertEquals("getCanonicalHostName", "localhost", name, response);

    //getHostAddress should return the loopback address
    String address = localHost.getHostAddress();
    assertEquals("getHostAddress", "127.0.0.1", address, response);

    //getHostName
    name = localHost.getHostName();
    assertEquals("getHostName", "localhost", name, response);

    //Misc Properties
    assertFalse("isAnyLocalAddress", localHost.isAnyLocalAddress(), response);
    assertFalse("isLinkLocalAddress", localHost.isLinkLocalAddress(), response);
    assertTrue("isLoopbackAddress", localHost.isLoopbackAddress(), response);
    assertFalse("isLoopbackAddress", localHost.isMCGlobal(), response);
    assertFalse("isMCLinkLocal", localHost.isMCLinkLocal(), response);
    assertFalse("isMCNodeLocal", localHost.isMCNodeLocal(), response);
    assertFalse("isMCOrgLocal", localHost.isMCOrgLocal(), response);
    assertFalse("isMCSiteLocal", localHost.isMCSiteLocal(), response);
    assertFalse("isMulticastAddress", localHost.isMulticastAddress(), response);
    assertFalse("isSiteLocalAddress", localHost.isSiteLocalAddress(), response);

    //toString
    assertEquals("toString", "localhost/127.0.0.1", localHost.toString(), response);

    //isReachable
    assertFalse("isReachable", localHost.isReachable(1000), response);
    //Can't test version of isReachable() that takes a NetworkInterface
    //because it is not possible to get a network interface without triggering the ptrace sandbox
    //localHost.isReachable(netif,  ttl,  timeout);

  }

  /**
   * Test properties of an instance of Inet4Address obtained via getLocalHost(). This method
   * is identical to testGetLocalHost(InetAddress, HttpServletResponse),  except that the
   * parameter is of type Inet4Address instead of InetAddress. This will test a different
   * code-path through our byte-rewriting.
   *
   * @param localHost hhe instance of InetAddress being tested
   * @param response HttpServletResponse used to return failure message
   * @throws IOException if method being tested throws this
   * @throws AssertionFailedException if assertion fails
   */
  private static void testLocalHost4(Inet4Address localHost, HttpServletResponse response)
      throws IOException, AssertionFailedException {
    assertNotNull("localhost", localHost, response);
    assertTrue("instanceof Inet4Addr", localHost instanceof Inet4Address, response);

    //getAddress
    byte[] bytes = localHost.getAddress();
    assertNotNull("localhost address-bytes", bytes, response);
    assertEquals("localhost address bytes.length", 4, bytes.length, response);
    assertEquals("first byte of localhost address", 127, bytes[0], response);
    assertEquals("last byte of localhost address", 1, bytes[3], response);

    String name = localHost.getCanonicalHostName();
    assertEquals("getCanonicalHostName", "localhost", name, response);

    //getHostAddress should return the loopback address
    String address = localHost.getHostAddress();
    assertEquals("getHostAddress", "127.0.0.1", address, response);

    //getHostName
    name = localHost.getHostName();
    assertEquals("getHostName", "localhost", name, response);

    //Misc Properties
    assertFalse("isAnyLocalAddress", localHost.isAnyLocalAddress(), response);
    assertFalse("isLinkLocalAddress", localHost.isLinkLocalAddress(), response);
    assertTrue("isLoopbackAddress", localHost.isLoopbackAddress(), response);
    assertFalse("isLoopbackAddress", localHost.isMCGlobal(), response);
    assertFalse("isMCLinkLoca", localHost.isMCLinkLocal(), response);
    assertFalse("isMCNodeLocal", localHost.isMCNodeLocal(), response);
    assertFalse("isMCOrgLocal", localHost.isMCOrgLocal(), response);
    assertFalse("isMCSiteLoca", localHost.isMCSiteLocal(), response);
    assertFalse("isMulticastAddress", localHost.isMulticastAddress(), response);
    assertFalse("isSiteLocalAddress", localHost.isSiteLocalAddress(), response);

    //toString
    assertEquals("toString", "localhost/127.0.0.1", localHost.toString(), response);

    //isReachable
    assertFalse("isReachable", localHost.isReachable(1000), response);
    //Can't test version of isReachable() that takes a NetworkInterface
    //because it is not possible ot get a network interface without trigger ptrace sandbox
    //localHost.isReachable(netif,  ttl,  timeout);
  }

  /**
   * Get instances of InetAddress by calling InetAddress.getByAddress(byte[]),
   * Inet4Address.getByAddress(byte[]),  and Inet6Address.getByAddress(byte[]),
   * passing in a 4-byte address,
   * and then test their properties.
   * @param response HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testGetByAddress4(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    byte[] addressBytes = new byte[] {74, 125, (byte) 224, 19};
    String addressString = "74.125.224.19";
    String name = null;
    InetAddress googleDotCom = InetAddress.getByAddress(addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
    googleDotCom = Inet4Address.getByAddress(addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
    googleDotCom = Inet6Address.getByAddress(addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
  }

  /**
   * Get instances of InetAddress by calling InetAddress.getByAddress(String, byte[]),
   * Ine4tAddress.getByAddress(String, byte[]),  and Inet6Address.getByAddress(String, byte[]),
   * passing in a 4-byte address,
   * and then test their properties.
   * @param response HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testGetByNameAndAddress4(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    byte[] addressBytes = new byte[] {74, 125, (byte) 224, 19};
    String addressString = "74.125.224.19";
    String name = "www.google.com";
    InetAddress googleDotCom = InetAddress.getByAddress(name, addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
    googleDotCom = Inet4Address.getByAddress(name, addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
    googleDotCom = Inet6Address.getByAddress(name, addressBytes);
    testNameAndAddress4(name, addressString, addressBytes, googleDotCom, response);
  }

  /**
   * Test the properties of an instance of IPV4 InetAddress obtained via getByAddress()
   * @param The expected host name
   * @param The expected addressString
   * @param The expected addressBytes
   * @param iAddr The instance of InetAddress being tested.
   * @param HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testNameAndAddress4(
      String name,
      String addressString,
      byte[] addressBytes,
      InetAddress iAddr,
      HttpServletResponse response)
      throws IOException, AssertionFailedException {

    assertNotNull("iAdr", iAddr, response);
    assertTrue("instanceof Inet4Addr", iAddr instanceof Inet4Address, response);

    //getAddress
    byte[] bytes = iAddr.getAddress();
    assertNotNull("iAddr bytes", bytes, response);
    assertEquals("iAddr bytes.length", 4, bytes.length, response);
    for (int i = 0; i < 4; i++) {
      assertEquals("iAddr address byte " + i, addressBytes[i], bytes[i], response);
    }

    //getCanonicalHostName should return addressString because user code
    //doesn't have permission to get an actual host name
    String canonicalName = iAddr.getCanonicalHostName();
    assertEquals("getCanonicalHostName", addressString, canonicalName, response);

    //getHostAddress
    String address = iAddr.getHostAddress();
    assertEquals("getHostAddress", addressString, address, response);

    //getHostName.
    String name2 = iAddr.getHostName();
    String expectedName = (name == null ? addressString : name);
    assertEquals("getHostName", expectedName, name2, response);

    //Misc Properties
    assertFalse("isAnyLocalAddress", iAddr.isAnyLocalAddress(), response);
    assertFalse("isLinkLocalAddress", iAddr.isLinkLocalAddress(), response);
    assertFalse("isLoopbackAddress", iAddr.isLoopbackAddress(), response);
    assertFalse("isMCGlobal", iAddr.isMCGlobal(), response);
    assertFalse("isMCLinkLoca", iAddr.isMCLinkLocal(), response);
    assertFalse("isMCNodeLocal", iAddr.isMCNodeLocal(), response);
    assertFalse("isMCOrgLocal", iAddr.isMCOrgLocal(), response);
    assertFalse("isMCSiteLoca", iAddr.isMCSiteLocal(), response);
    assertFalse("isMulticastAddress", iAddr.isMulticastAddress(), response);
    assertFalse("isSiteLocalAddress", iAddr.isSiteLocalAddress(), response);

    //toString
    String prefix = (name == null ? addressString : name);
    assertEquals("toString", prefix + "/" + addressString, iAddr.toString(), response);

    //isReachable
    assertFalse("isReachable", iAddr.isReachable(1000), response);
  }

  /**
   * Get instances of InetAddress by calling InetAddress.getByAddress(String, byte[]),
   * and passing in a 16-byte address. Then test their properties.
   * @param response HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testGetByAddress6(HttpServletResponse response)
      throws IOException, AssertionFailedException {

    //Passing in 17-bytes should throw an exception
    byte[] badAddr = new byte[17];
    UnknownHostException caught = null;
    try {
      InetAddress.getByAddress(badAddr);
    } catch (UnknownHostException e) {
      caught = e;
    }
    assertNotNull("caught", caught, response);

    //Next we try an Ipv4-apped address
    byte[] mappedAddr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 127, 0, 0, 1};
    //square brackets should get stripped in name
    Inet4Address inet4Addr = (Inet4Address) InetAddress.getByAddress("[localhost]", mappedAddr);
    testLocalHost(inet4Addr, response);

    //Now we test a real IPv6 address
    byte[] addr =
        new byte[] {0x10, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 8, 8, 0, 0x20, 0xC, 0x41, 0x7A};
    String addressString = "1080:0:0:0:8:800:200C:417A";
    String hostName = "my.host";
    Inet6Address inet6Addr = (Inet6Address) InetAddress.getByAddress(hostName, addr);
    testNameAndAddress6(hostName, addressString, addr, 0, inet6Addr, response);

    //Now we test a real IPv6 address with a scope ID
    int scopeId = 17;
    addressString = "1080:0:0:0:8:800:200C:417A%17";
    inet6Addr = Inet6Address.getByAddress(hostName, addr, scopeId);
    testNameAndAddress6(hostName, addressString, addr, scopeId, inet6Addr, response);
  }

  /**
   * Test the properties of an instance of IPV6 InetAddress. Much of the code in this method
   * is the same as the code in the method testNameAndAddress4() but it will test different
   * code paths through our byte-code-rewriting since this method uses an instance of
   * Inet6Address.
   * @param hostName The expected host name
   * @param addressString The expected address string
   * @param addressBytes The expected address bytes
   * @param scopeId the expected scope id
   * @param inet6Addr The instance of Inet6Address being tested
   * @param response HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testNameAndAddress6(
      String hostName,
      String addressString,
      byte[] addressBytes,
      int scopeId,
      Inet6Address inet6Addr,
      HttpServletResponse response)
      throws IOException, AssertionFailedException {

    assertNotNull("inet6Addr", inet6Addr, response);

    //getAddress
    byte[] bytes = inet6Addr.getAddress();
    assertNotNull("inet6Addr bytes", bytes, response);
    assertEquals("inet6Addr bytes.length", 16, bytes.length, response);
    for (int i = 0; i < 16; i++) {
      assertEquals("inet6Addr address byte " + i, addressBytes[i], bytes[i], response);
    }

    //getCanonicalHostName should return addressString because user code
    //doesn't have permission to get an actual host name
    String canonicalName = inet6Addr.getCanonicalHostName();
    assertEquals("getCanonicalHostName", addressString, canonicalName.toUpperCase(), response);

    //getHostAddress
    String address = inet6Addr.getHostAddress();
    assertEquals("getHostAddress", addressString, address.toUpperCase(), response);

    //getHostName.
    String name2 = inet6Addr.getHostName().toUpperCase();
    String expectedName = (hostName == null ? addressString : hostName.toUpperCase());
    assertEquals("getHostName", expectedName, name2, response);

    //Misc Properties
    assertFalse("isAnyLocalAddress", inet6Addr.isAnyLocalAddress(), response);
    assertFalse("isLinkLocalAddress", inet6Addr.isLinkLocalAddress(), response);
    assertFalse("isLoopbackAddress", inet6Addr.isLoopbackAddress(), response);
    assertFalse("isMCGlobal", inet6Addr.isMCGlobal(), response);
    assertFalse("isMCLinkLoca", inet6Addr.isMCLinkLocal(), response);
    assertFalse("isMCNodeLocal", inet6Addr.isMCNodeLocal(), response);
    assertFalse("isMCOrgLocal", inet6Addr.isMCOrgLocal(), response);
    assertFalse("isMCSiteLoca", inet6Addr.isMCSiteLocal(), response);
    assertFalse("isMulticastAddress", inet6Addr.isMulticastAddress(), response);
    assertFalse("isSiteLocalAddress", inet6Addr.isSiteLocalAddress(), response);

    //toString
    String str = inet6Addr.toString();
    String prefix = (hostName == null ? addressString : hostName);
    assertEquals(
        "toString", (prefix + "/" + addressString).toUpperCase(), str.toUpperCase(), response);

    //isReachable
    assertFalse("isReachable", inet6Addr.isReachable(1000), response);

    //getScopedInterface
    assertNull("getScopedInterface()", inet6Addr.getScopedInterface(), response);

    //getScopedID
    assertEquals("getScopedID()", scopeId, inet6Addr.getScopeId(), response);

    //getHashCode
    assertFalse("hashCode is 0", 0 == inet6Addr.hashCode(), response);

    //isIPv4Compatible
    assertFalse("is Ipv4Compatible()", inet6Addr.isIPv4CompatibleAddress(), response);
  }

  /**
   * Test InetAddress.getByName(String) Inet4Address.getByName(String) and
   * Inet6Addr.getByName(String)
   * @param response HttpServletResponse used to return failure message
   * @throws IOException If method being tested throws this.
   * @throws AssertionFailedException If an assertion fails
   */
  private static void testGetByName(HttpServletResponse response)
      throws IOException, AssertionFailedException {

    // Getting any address returns 10.1.1.1 with the MockDelegate delegate.
    assertEquals(
        "InetAddress getByName",
        MockDelegate.MOCK_RESPONSE,
        InetAddress.getByName("www.google.com"),
        response);
    assertEquals(
        "Inet4Address getByName",
        MockDelegate.MOCK_RESPONSE,
        Inet4Address.getByName("www.google.com"),
        response);
    assertEquals(
        "Inet6Address getByName",
        MockDelegate.MOCK_RESPONSE,
        Inet6Address.getByName("www.google.com"),
        response);

    //Passing in null gives you the loopback address
    InetAddress localHost = InetAddress.getByName(null);
    testLocalHost(localHost, response);

    //Now we try passing in an IPV4 address string as the name
    String name = null;
    String addressStr = "123.17.0.77";
    byte[] addressBytes = new byte[] {123, 17, 0, 77};
    InetAddress inetAddr = InetAddress.getByName(addressStr);
    TestInetAddressServlet.testNameAndAddress4(name, addressStr, addressBytes, inetAddr, response);
    inetAddr = Inet4Address.getByName(addressStr);
    TestInetAddressServlet.testNameAndAddress4(name, addressStr, addressBytes, inetAddr, response);
    inetAddr = Inet6Address.getByName(addressStr);
    testNameAndAddress4(name, addressStr, addressBytes, inetAddr, response);

    //Now we try passing in an IPV6 address string as the name
    addressStr = "1080:0:0:0:8:800:200C:417A";
    addressBytes =
        new byte[] {0x10, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 8, 8, 0, 0x20, 0xC, 0x41, 0x7A};
    inetAddr = InetAddress.getByName(addressStr);
    assertTrue("instanceof Inet6Address", inetAddr instanceof Inet6Address, response);
    Inet6Address inet6Addr = (Inet6Address) inetAddr;
    int scopeId = 0;
    testNameAndAddress6(name, addressStr, addressBytes, scopeId, inet6Addr, response);
    inetAddr = Inet4Address.getByName(addressStr);
    assertTrue("instanceof Inet6Address", inetAddr instanceof Inet6Address, response);
    inet6Addr = (Inet6Address) inetAddr;
    testNameAndAddress6(name, addressStr, addressBytes, scopeId, inet6Addr, response);
    inetAddr = Inet6Address.getByName(addressStr);
    assertTrue("instanceof Inet6Address", inetAddr instanceof Inet6Address, response);
    inet6Addr = (Inet6Address) inetAddr;
    testNameAndAddress6(name, addressStr, addressBytes, scopeId, inet6Addr, response);

    //Now we try passing in an IPV6 address with a numeric scope ID as the name
    addressStr = "1080:0:0:0:8:800:200C:417A%42";
    scopeId = 42;
    addressBytes =
        new byte[] {0x10, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 8, 8, 0, 0x20, 0xC, 0x41, 0x7A};
    inetAddr = InetAddress.getByName(addressStr);
    assertTrue("instanceof Inet6Address", inetAddr instanceof Inet6Address, response);
    inet6Addr = (Inet6Address) inetAddr;
    testNameAndAddress6(name, addressStr, addressBytes, scopeId, inet6Addr, response);

    //Now we try passing in an IPV6 address with a non-numeric interface name as the name
    addressStr = "1080:0:0:0:8:800:200C:417A%MyFavoriteInterface";

    // Trying to get an address with an interface name throws an exception.
    SecurityException caught = null;
    try {
      inetAddr = InetAddress.getByName(addressStr);
    } catch (SecurityException e) {
      caught = e;
    }
    assertNotNull("caught", caught, response);
  } //testGetByName

  /**
   * Tests NetworkInterface.getNetworkInterfaces(), NetworkInterface.getByName(String).
   * The mirrors for these methods do nothing but return null.
   */
  private static void testNetworkInterface(HttpServletResponse response)
      throws IOException, AssertionFailedException {
    Enumeration<NetworkInterface> allInterfaces = NetworkInterface.getNetworkInterfaces();
    assertNull("NetworkInterface.getNetworkInterfaces()", allInterfaces, response);
    NetworkInterface netInf = NetworkInterface.getByName("foo");
    assertNull("NetworkInterface.getByName()", netInf, response);
    InetAddress localHost = InetAddress.getLocalHost();
    netInf = NetworkInterface.getByInetAddress(localHost);
  }

  /**
   * A mock ApiProxy.Delegate specifically for handling resolve calls.
   */
  private static class MockDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    static final byte[] MOCK_IPV4_ADDR = {10, 1, 1, 1};
    static final InetAddress MOCK_RESPONSE = makeMockResponseAddress();

    private static final InetAddress makeMockResponseAddress() {
      try {
        return InetAddress.getByAddress(MOCK_IPV4_ADDR);
      } catch (UnknownHostException e) {
        // This should never happen.
        throw new RuntimeException(e);
      }
    }

    private static final byte[] RESOLVER_RESPONSE = makeResolverResponse();

    static byte[] makeResolverResponse() {
      ResolveReply response = new ResolveReply();

      response.addPackedAddressAsBytes(MOCK_IPV4_ADDR);
      return response.toByteArray();
    }

    @Override
    public byte[] makeSyncCall(
        ApiProxy.Environment environment, String packageName, String methodName, byte[] request) {
      if ("remote_socket".equals(packageName) && "Resolve".equals(methodName)) {
        return RESOLVER_RESPONSE;
      }
      throw new UnsupportedOperationException();
    }

    @Override
    public Future<byte[]> makeAsyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      if ("remote_socket".equals(packageName) && "Resolve".equals(methodName)) {
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
            return RESOLVER_RESPONSE;
          }

          @Override
          public byte[] get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            return RESOLVER_RESPONSE;
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
}

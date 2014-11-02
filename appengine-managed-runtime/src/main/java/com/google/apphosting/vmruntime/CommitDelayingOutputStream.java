/**
 * Copyright 2014 Google Inc. All Rights Reserved.
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

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * An implementation of {@link ServletOutputStream} wrapping an OutputStream object. Writes are
 * forwarded to the underlying object immediately. Calls that can trigger either a flush or a close
 * are delayed until {@code CommitDelayingOutputStream#closeIfClosed} and
 * {@code CommitDelayingOutputStream#flushIfFlushed} are called respectively.
 *
 * <p>This implementation is mimicking the behavior of the {@link ServletOutputStream} returned by
 * the HTTP {@link org.eclipse.jetty.server.Response} implementation in Jetty9 with the important
 * difference that no user visible methods are able to commit the response. A committed response
 * means that the HTTP response headers have been sent to the user. For our purposes this means that
 * any changes to HTTP headers after this point are ignored and that any API calls after that point
 * might fail.
 *
 */
class CommitDelayingOutputStream extends ServletOutputStream {
  static final int MAX_RESPONSE_SIZE_BYTES = 32 * 1024 * 1024;
  private int bufferSize = MAX_RESPONSE_SIZE_BYTES;

  static final int MAX_RESPONSE_HEADERS_SIZE_BYTES = 8192;

  private int bytesWritten = 0;

  private boolean closed = false;
  private boolean flushed = false;

  private long contentLength = -1;
  private boolean contentLengthSet = false;

  private final OutputStream wrappedOutputStream;

  /**
   * Creates a new CommitDelayingOutputStream object.
   *
   * @param wrappedOutputStream The OutputStream to forward writes to.
   */
  CommitDelayingOutputStream(OutputStream wrappedOutputStream) {
    this.wrappedOutputStream = wrappedOutputStream;
  }

  /**
   * Updates the number of bytes written to the stream. The stream is marked as flushed if the
   * buffer size or content length has been reached.
   *
   * @param num The number of bytes written.
   */
  private void bytesWritten(int num) {
    bytesWritten += num;
    if (flushed) {
      return;
    }
    if (bytesWritten >= bufferSize) {
      flushed = true;
      return;
    }
    if (contentLengthSet && bytesWritten >= contentLength) {
      flushed = true;
    }
  }

  /**
   * Verifies that this OutputStream is writable.
   *
   * @throws IOException If the OutputStream is closed.
   */
  private void ensureWritable() throws IOException {
    if (closed) {
      throw new IOException("Closed");
    }
  }

  /**
   * Mark this stream as closed without forwarding the close call to the underlying stream. Writes
   * to this object will still behave as if the stream is closed (i.e. throw an IOException) but the
   * underlying stream is not closed until {@code CommitDelayingOutputStream#closeIfClosed()} is
   * called.
   */
  @Override
  public void close() {
    flushed = true;
    closed = true;
  }

  /**
   * Close the underlying stream if close() has been called on this object. This is a no-op if
   * close() never was called on this object.
   *
   * @throws IOException If an IOException occurred when closing the underlying stream.
   */
  void closeIfClosed() throws IOException {
    if (closed) {
      wrappedOutputStream.close();
    }
  }

  /**
   * Marks this stream as flushed. The flush is not forwarded to the underlying stream. Instead
   * flush is called on that stream when {@code CommitDelayingOutputStream#flushIfFlushed()} is
   * called.
   *
   * @throws IOException If the stream is closed.
   */
  @Override
  public void flush() throws IOException {
    ensureWritable();
    flushed = true;
  }

  /**
   * Flush the underlying stream any action has been performed that would have resulted in a flush
   * (through flush(), setBufferSize(), or setContentLength().
   *
   * @throws IOException If an IOException occurred when closing the underlying stream.
   */
  void flushIfFlushed() throws IOException {
    if (flushed) {
      wrappedOutputStream.flush();
    }
  }

  /**
   * @return The buffer size of this stream.
   */
  int getBufferSize() {
    return bufferSize;
  }

  /**
   * Sets the buffer size of this stream. If the number of bytes written is greater than or equal to
   * the new buffer size the stream is marked as flushed.
   *
   * @param bufferSize The new buffer size.
   */
  void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
    if (bytesWritten >= bufferSize) {
      flushed = true;
    }
  }

  /**
   * @return The number of bytes written to this stream.
   */
  int getBytesWritten() {
    return bytesWritten;
  }

  /**
   * Clear any content length set on this stream.
   */
  void clearContentLength() {
    this.contentLength = -1;
    this.contentLengthSet = false;
  }

  /**
   * Returns the content length used by this steam. Note: this is content-length in the context of
   * the HTTP header and is different from the number of bytes written to the stream so far.
   *
   * @return The content length used by this steam.
   */
  long getContentLength() {
    return contentLength;
  }

  /**
   * @return true if content length has been set, false otherwise.
   */
  boolean hasContentLength() {
    return contentLengthSet;
  }

  /**
   * Sets the content length to be used by this steam. If the number of bytes written is equal or
   * greater than the new content length the stream is marked as flushed. Note: this is
   * content-length in the context of the HTTP header and is different from the number of bytes
   * written to the stream so far.
   *
   */
  void setContentLength(long contentLength) {
    this.contentLengthSet = true;
    this.contentLength = contentLength;
    if (bytesWritten >= contentLength) {
      flushed = true;
    }
  }

  /**
   * Returns true if the response is committed (i.e. flushed or closed). This is in an HTTP context
   * where a committed response means that any HTTP headers have been sent to the user.
   *
   * @return True if the response is committed, false otherwise.
   */
  boolean isCommitted() {
    return closed || flushed;
  }

  /**
   * Resets the stream by setting the number of bytes written to zero. Note: the underlying stream
   * must be reset by calling reset() on the parent {@code HttpServletResponse}.
   */
  void reset() {
    bytesWritten = 0;
  }

  /**
   * Make sure we don't go over the max buffer size, otherwise jetty's HttpOutput will
   * automatically commit the stream, which defeats the purpose of this class.
   */
  private void checkResponseSize(int bytesToWrite) throws IOException {
    if (bytesWritten + bytesToWrite > MAX_RESPONSE_SIZE_BYTES - MAX_RESPONSE_HEADERS_SIZE_BYTES) {
      throw new IOException("Max response size exceeded.");
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    checkResponseSize(b.length);
    ensureWritable();
    wrappedOutputStream.write(b);
    bytesWritten(b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    checkResponseSize(len);
    ensureWritable();
    wrappedOutputStream.write(b, off, len);
    bytesWritten(len);
  }

  @Override
  public void write(int b) throws IOException {
    checkResponseSize(1);
    ensureWritable();
    wrappedOutputStream.write(b);
    bytesWritten(1);
  }

  @Override
  public void setWriteListener(WriteListener writeListener) {
  }

  @Override
  public boolean isReady() {
    return true;
  }
}

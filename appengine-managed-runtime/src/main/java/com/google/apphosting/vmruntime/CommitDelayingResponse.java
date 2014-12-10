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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Wraps a {@link HttpServletResponse} so that no user actions can trigger the response to be
 * committed before any cleanup tasks can be completed by the VmRuntimeWebAppContext.
 *
 * <p>Several AppEngine APIs requires the security ticket associated with an active request. Because
 * of the Content-Length header it is possible for the user to "finish" the response before all API
 * calls are completed. As soon as the proxy sees the last byte of the response, it sends it off to
 * the appserver. The appserver then immediately sends it to the user. At that point the ticket used
 * to identify API requests becomes invalid.
 *
 * <p>See meta_app.py for the corresponding Python implementation.
 *
 */
public class CommitDelayingResponse extends HttpServletResponseWrapper {
  protected static final String CONTENT_LENGTH = "Content-Length";

  private interface PendingCall {
    void commit() throws IOException;
  }

  /**
   * The current output mode of the response. Either getOutputStream or getWriter can be used to
   * write the body of the response but not both. See {@link javax.servlet.ServletResponse}.
   */
  private enum OutputMode {
    NEW,
    WRITER,
    OUTPUT_STREAM;
  }

  private OutputMode mode = OutputMode.NEW;
  private PrintWriter writer = null;

  private PendingCall pending = null;

  /**
   * Subclasses may access this object to read content length information stored in it.
   */
  protected final CommitDelayingOutputStream output;

  /**
   * Create a new @code{CommitDelayingResponse} wrapping the provided @code{HttpServletResponse}.
   *
   * @param response The response to forward operations to.
   * @throws IOException
   */
  public CommitDelayingResponse(HttpServletResponse response) throws IOException {
    super(response);
    this.output = new CommitDelayingOutputStream(super.getOutputStream());
  }

  /**
   * Commit any pending changes to the wrapped response.
   *
   * @throws IOException
   */
  public void commit() throws IOException {
    if (pending != null) {
      pending.commit();
      return;
    }
    if (output.hasContentLength()) {
      super.setHeader(CONTENT_LENGTH, Long.toString(output.getContentLength()));
    }
    output.flushIfFlushed();
    if (writer != null) {
      writer.close();
    }
    output.closeIfClosed();
  }

  /**
   * Override flushBuffer from HttpServletResponse. Instead on immediately flushing the buffer the
   * action is recorded and executed when @code{CommitDelayingResponse#commit()} is called.
   */
  @Override
  public void flushBuffer() throws IOException {
    output.flush();
  }

  @Override
  public int getBufferSize() {
    return output.getBufferSize();
  }

  /**
   * Returns a ServletOutputStream where all writes are forwarded on to the OutputStream of the
   * wrapped response. The important difference is that calls to close() and flush() are not
   * forwarded but recorded and executed when @code{CommitDelayingResponse#commit()} is called.
   *
   * @see javax.servlet.ServletResponse#getOutputStream()
   */
  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (mode == OutputMode.WRITER) {
      throw new IllegalStateException("WRITER");
    }
    mode = OutputMode.OUTPUT_STREAM;
    return output;
  }

  /**
   * Returns a PrintWriter where all writes are encoded using the encoding returned by
   * {@link #getCharacterEncoding} and forwarded on to the OutputStream returned by
   * {@link #getOutputStream}. All calls to close() or flush() are not forwarded but recorded and
   * executed when @code{CommitDelayingResponse#commit()} is called.
   *
   * @see javax.servlet.ServletResponse#getWriter()
   */
  @Override
  public PrintWriter getWriter() throws IOException {
    if (mode == OutputMode.OUTPUT_STREAM) {
      throw new IllegalStateException("STREAM");
    }
    mode = OutputMode.WRITER;
    if (writer == null) {
      writer = new PrintWriter(new OutputStreamWriter(output, getCharacterEncoding()));
    }
    return writer;
  }

  /**
   * Returns true if an operation was made that would have committed the response, false otherwise.
   */
  @Override
  public boolean isCommitted() {
    return pending != null || output.isCommitted();
  }

  @Override
  public void reset() {
    if (isCommitted()) {
      throw new IllegalStateException("Committed");
    }
    writer = null;
    mode = OutputMode.NEW;
    output.reset();
    super.reset();
  }

  @Override
  public void resetBuffer() {
    if (isCommitted()) {
      throw new IllegalStateException("Committed");
    }
    output.reset();
    super.resetBuffer();
  }

  /**
   * Convenience method equivalent of sendError(sc, null).
   */
  @Override
  public void sendError(int sc) {
    sendError(sc, null);
  }

  /**
   * Override sendError from HttpServletResponse. Instead on immediately sending the error the
   * action is recorded and executed when @code{CommitDelayingResponse#commit()} is called.
   */
  @Override
  public void sendError(final int sc, final String msg) {
    if (isCommitted()) {
      throw new IllegalStateException("Committed");
    }
    pending = new PendingCall() {
      @Override
      public void commit() throws IOException {
        CommitDelayingResponse.super.sendError(sc, msg);
      }
    };
  }

  /**
   * Override sendRedirect from HttpServletResponse. Instead on immediately sending the redirect the
   * action is recorded and executed when @code{CommitDelayingResponse#commit()} is called.
   */
  @Override
  public void sendRedirect(final String location) {
    if (isCommitted()) {
      throw new IllegalStateException("Committed");
    }
    pending = new PendingCall() {
      @Override
      public void commit() throws IOException {
        CommitDelayingResponse.super.sendRedirect(location);
      }
    };
  }

  /**
   * Override setBufferSize from ServletResponse. We are not forwarding this operation to the
   * wrapped response as it might trigger a flush. Instead it is reported to the output buffer
   * so it can use the information to determine when a response would have been committed.
   *
   * @see javax.servlet.ServletResponse#setBufferSize(int)
   */
  @Override
  public void setBufferSize(int size) {
    output.setBufferSize(size);
  }

  @Override
  public void setContentLength(int len) {
    output.setContentLength(len);
  }

  private void handleContentLengthHeader(String value) {
    if (value == null) {
      output.clearContentLength();
      return;
    }
    output.setContentLength(Long.parseLong(value));
  }

  @Override
  public void setHeader(String name, String value) {
    if (CONTENT_LENGTH.equalsIgnoreCase(name)) {
      handleContentLengthHeader(value);
      return;
    }
    super.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    if (CONTENT_LENGTH.equalsIgnoreCase(name)) {
      handleContentLengthHeader(value);
      return;
    }
    super.addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    if (CONTENT_LENGTH.equalsIgnoreCase(name)) {
      setContentLength(value);
      return;
    }
    super.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    if (CONTENT_LENGTH.equalsIgnoreCase(name)) {
      setContentLength(value);
      return;
    }
    super.addIntHeader(name, value);
  }

  @Override
  public boolean containsHeader(String name) {
    return CONTENT_LENGTH.equalsIgnoreCase(name) ? output.hasContentLength()
            : super.containsHeader(name);
  }
}

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

import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse.IncrementStatusCode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddResponse.TaskResult;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.apphosting.api.ApiBasePb.VoidProto;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DatastorePb.Transaction;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;

import java.net.HttpURLConnection;

//import com.google.apphosting.datastore.DatastoreV3Pb.Transaction;

/**
 * Misc individual Jetty9 vmengines tests.
 */
public class VmRuntimeJettyKitchenSink2IT extends VmRuntimeTestBase {

  /**
   * Test that the count servlet was loaded, and that local state is preserved
   * between requests.
   */
  public void testCountLocal() throws Exception {
    for (int i = 1; i <= 5; i++) {
      String[] lines = fetchUrl(createUrl("/count?type=local"));
      assertEquals(1, lines.length);
      assertEquals("" + i, lines[0].trim());
    }
  }

  /**
   * Test that abandoned transactions are aborted when the request completes.
   */
  public void testAbandonTransaction() throws Exception {
    long transactionId = 123456789012345L;
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);
    // If the filter is installed we expect 2 requests.
    // First: a call to datastore_v3/BeginTransaction
    // Second: a call to datastore_v3/Rollback
    Transaction transactionResponse = new Transaction();
    transactionResponse.setHandle(transactionId);
    transactionResponse.setApp(TestMetadataServer.PROJECT);
    fakeApiProxy.addApiResponse(transactionResponse); // Response to datastore_v3/BeginTransaction.
    fakeApiProxy.addApiResponse(new VoidProto()); // Response to datastore_v3/Rollback.

    String[] lines = fetchUrl(createUrl("/abandonTxn"));
    assertEquals(1, lines.length);
    assertEquals("" + transactionId, lines[0].trim());
    assertEquals(2, fakeApiProxy.requests.size());
    assertEquals("BeginTransaction", fakeApiProxy.requests.get(0).methodName);
    assertEquals("Rollback", fakeApiProxy.requests.get(1).methodName);
  }

  /**
   * Test that blob upload requests are intercepted by the blob upload filter.
   */
  public void testBlobUpload() throws Exception {
    String postData =
        "--==boundary\r\n"
            + "Content-Type: message/external-body; "
            + "charset=ISO-8859-1; blob-key=\"blobkey:blob-0\"\r\n"
            + "Content-Disposition: form-data; "
            + "name=upload-0; filename=\"file-0.jpg\"\r\n"
            + "\r\n"
            + "Content-Type: image/jpeg\r\n"
            + "Content-Length: 1024\r\n"
            + "X-AppEngine-Upload-Creation: 2009-04-30 17:12:51.675929\r\n"
            + "Content-Disposition: form-data; "
            + "name=upload-0; filename=\"file-0.jpg\"\r\n"
            + "\r\n"
            + "\r\n"
            + "--==boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=text1\r\n"
            + "Content-Length: 10\r\n"
            + "\r\n"
            + "Testing.\r\n"
            + "\r\n"
            + "\r\n"
            + "--==boundary--";

    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    PostMethod blobPost = new PostMethod(createUrl("/upload-blob").toString());
    blobPost.addRequestHeader("Content-Type", "multipart/form-data; boundary=\"==boundary\"");
    blobPost.addRequestHeader("X-AppEngine-BlobUpload", "true");
    blobPost.setRequestBody(postData);
    int httpCode = httpClient.executeMethod(blobPost);

    assertEquals(302, httpCode);
    Header redirUrl = blobPost.getResponseHeader("Location");
    assertEquals(
        "http://" + getServerHost() + "/serve-blob?key=blobkey:blob-0", redirUrl.getValue());
  }

  /**
   * Test that the count servlet was loaded, and that memcache calls are
   * forwarded through the VmApiProxyDelegate.
   */
  public void testCountMemcache() throws Exception {
    // Replace the API proxy delegate so we can fake API responses.
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);

    for (int i = 1; i <= 5; i++) {
      MemcacheIncrementResponse responsePb =
          MemcacheIncrementResponse.newBuilder()
              .setIncrementStatus(IncrementStatusCode.OK)
              .setNewValue(i)
              .build();
      fakeApiProxy.addApiResponse(responsePb);
      String[] lines = fetchUrl(createUrl("/count?type=memcache"));
      assertEquals(1, lines.length);
      assertEquals("" + i, lines[0].trim());
    }
  }

  /**
   * Test that the deferredTask handler is installed.
   */
  public void testDeferredTask() throws Exception {
    // Replace the API proxy delegate so we can fake API responses.
    FakeableVmApiProxyDelegate fakeApiProxy = new FakeableVmApiProxyDelegate();
    ApiProxy.setDelegate(fakeApiProxy);

    // Add a api response so the task queue api is happy.
    TaskQueueBulkAddResponse taskAddResponse = new TaskQueueBulkAddResponse();
    TaskResult taskResult = taskAddResponse.addTaskResult();
    taskResult.setResult(ErrorCode.OK.getValue());
    taskResult.setChosenTaskName("abc");
    fakeApiProxy.addApiResponse(taskAddResponse);

    // Issue a deferredTaskRequest with payload.
    String testData = "0987654321acbdefghijklmn";
    String[] lines = fetchUrl(createUrl("/testTaskQueue?deferredTask=1&deferredData=" + testData));
    TaskQueueBulkAddRequest request = new TaskQueueBulkAddRequest();
    request.parseFrom(fakeApiProxy.getLastRequest().requestData);
    assertEquals(1, request.addRequestSize());
    TaskQueueAddRequest addRequest = request.getAddRequest(0);
    assertEquals(TaskQueueAddRequest.RequestMethod.POST.getValue(), addRequest.getMethod());

    // Pull out the request and fire it at the app.
    HttpClient httpClient = new HttpClient();
    httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
    PostMethod post = new PostMethod(createUrl(addRequest.getUrl()).toString());
    post.getParams().setVersion(HttpVersion.HTTP_1_0);
    // Add the required Task queue header, plus any headers from the request.
    post.addRequestHeader("X-AppEngine-QueueName", "1");
    for (TaskQueueAddRequest.Header header : addRequest.headers()) {
      post.addRequestHeader(header.getKey(), header.getValue());
    }
    post.setRequestEntity(new ByteArrayRequestEntity(addRequest.getBodyAsBytes()));
    int httpCode = httpClient.executeMethod(post);
    assertEquals(HttpURLConnection.HTTP_OK, httpCode);

    // Verify that the task was handled and that the payload is correct.
    lines = fetchUrl(createUrl("/testTaskQueue?getLastPost=1"));
    assertEquals("deferredData:" + testData, lines[lines.length - 1]);
  }
}

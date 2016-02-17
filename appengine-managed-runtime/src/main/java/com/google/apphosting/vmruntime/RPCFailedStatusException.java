package com.google.apphosting.vmruntime;

import com.google.apphosting.api.ApiProxy;

public class RPCFailedStatusException extends ApiProxy.RPCFailedException {
  private final int statusCode;
  public RPCFailedStatusException(String packageName, String methodName, int statusCode)
  {
    super(packageName, methodName);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}

package org.idp.server.core.authentication.sms;

public enum SmsAuthenticationExecutionStatus {
  OK(200), CLIENT_ERROR(400), SERVER_ERROR(500);

  int statusCode;

  SmsAuthenticationExecutionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }

  public boolean isClientError() {
    return this == CLIENT_ERROR;
  }

  public boolean isServerError() {
    return this == SERVER_ERROR;
  }

  public int code() {
    return statusCode;
  }
}

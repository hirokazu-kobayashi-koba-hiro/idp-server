package org.idp.server.handler.io.status;

public enum TokenIntrospectionRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  INVALID_TOKEN(200),
  SERVER_ERROR(500);

  int statusCode;

  TokenIntrospectionRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
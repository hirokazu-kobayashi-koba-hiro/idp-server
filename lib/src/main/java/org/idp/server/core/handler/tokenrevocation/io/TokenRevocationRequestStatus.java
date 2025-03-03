package org.idp.server.core.handler.tokenrevocation.io;

public enum TokenRevocationRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  SERVER_ERROR(500);

  int statusCode;

  TokenRevocationRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}

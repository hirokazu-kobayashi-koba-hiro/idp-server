package org.idp.server.io.status;

public enum TokenRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  UNAUTHORIZE(401),
  SERVER_ERROR(500);

  int statusCode;

  TokenRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public int statusCode() {
    return statusCode;
  }
}

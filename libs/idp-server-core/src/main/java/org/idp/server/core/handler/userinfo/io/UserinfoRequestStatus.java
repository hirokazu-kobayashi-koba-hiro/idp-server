package org.idp.server.core.handler.userinfo.io;

public enum UserinfoRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  UNAUTHORIZE(401),
  SERVER_ERROR(500);

  int statusCode;

  UserinfoRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }
}

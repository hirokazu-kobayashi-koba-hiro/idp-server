package org.idp.server.core.ciba.handler.io;

public enum CibaRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  UNAUTHORIZE(401),
  SERVER_ERROR(500);

  int statusCode;

  CibaRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public int statusCode() {
    return statusCode;
  }
}

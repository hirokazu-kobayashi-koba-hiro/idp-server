package org.idp.server.handler.ciba.io;

public enum CibaDenyStatus {
  OK(200),
  BAD_REQUEST(400),
  SERVER_ERROR(500);

  int statusCode;

  CibaDenyStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public int statusCode() {
    return statusCode;
  }
}

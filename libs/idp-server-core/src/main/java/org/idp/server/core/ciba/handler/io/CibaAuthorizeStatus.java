package org.idp.server.core.ciba.handler.io;

public enum CibaAuthorizeStatus {
  OK(200),
  BAD_REQUEST(400),
  SERVER_ERROR(500);

  int statusCode;

  CibaAuthorizeStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public int statusCode() {
    return statusCode;
  }
}

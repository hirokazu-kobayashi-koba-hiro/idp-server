package org.idp.server.io.status;

public enum TokenRequestStatus {
  OK,
  BAD_REQUEST,
  UNAUTHORIZE,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }
}

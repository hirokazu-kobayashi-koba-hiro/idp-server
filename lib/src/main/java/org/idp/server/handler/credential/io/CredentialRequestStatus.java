package org.idp.server.handler.credential.io;

public enum CredentialRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  SERVER_ERROR(500);

  int statusCode;

  CredentialRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }
}

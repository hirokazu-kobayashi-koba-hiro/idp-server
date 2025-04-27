package org.idp.server.core.identity.verification;

public enum IdentityVerificationApplicationStatus {
  OK(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  IdentityVerificationApplicationStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }
}

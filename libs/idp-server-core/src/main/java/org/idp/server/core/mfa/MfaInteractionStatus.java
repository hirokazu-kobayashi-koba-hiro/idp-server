package org.idp.server.core.mfa;

public enum MfaInteractionStatus {
  SUCCESS(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  MfaInteractionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isError() {
    return this == CLIENT_ERROR || this == SERVER_ERROR;
  }
}

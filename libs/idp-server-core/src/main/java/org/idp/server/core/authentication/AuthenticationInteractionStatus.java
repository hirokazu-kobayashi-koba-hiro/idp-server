package org.idp.server.core.authentication;

public enum AuthenticationInteractionStatus {
  SUCCESS(200), CLIENT_ERROR(400), SERVER_ERROR(500);

  int statusCode;

  AuthenticationInteractionStatus(int statusCode) {
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

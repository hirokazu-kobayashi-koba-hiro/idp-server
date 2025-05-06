package org.idp.server.core.oidc.discovery.handler.io;

public enum JwksRequestStatus {
  OK(200), BAD_REQUEST(400), SERVER_ERROR(500);

  int statusCode;

  JwksRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}

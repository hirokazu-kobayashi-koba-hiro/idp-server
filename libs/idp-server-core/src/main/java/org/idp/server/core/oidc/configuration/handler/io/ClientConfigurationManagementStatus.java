package org.idp.server.core.oidc.configuration.handler.io;

public enum ClientConfigurationManagementStatus {
  OK(200), BAD_REQUEST(400), NOT_FOUND(404), SERVER_ERROR(500);

  int statusCode;

  ClientConfigurationManagementStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}

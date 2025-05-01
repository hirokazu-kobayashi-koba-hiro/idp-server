package org.idp.server.core.oidc.configuration.handler.io;

public enum ClientConfigurationManagementListStatus {
  OK(200),
  BAD_REQUEST(400),
  SERVER_ERROR(500);

  int statusCode;

  ClientConfigurationManagementListStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}

package org.idp.server.core.handler.configuration.io;

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

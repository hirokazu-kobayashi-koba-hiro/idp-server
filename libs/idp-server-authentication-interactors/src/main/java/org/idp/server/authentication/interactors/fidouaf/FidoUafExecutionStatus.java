package org.idp.server.authentication.interactors.fidouaf;

public enum FidoUafExecutionStatus {
  OK(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  FidoUafExecutionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }

  public boolean isClientError() {
    return this == CLIENT_ERROR;
  }

  public boolean isServerError() {
    return this == SERVER_ERROR;
  }

  public int code() {
    return statusCode;
  }
}

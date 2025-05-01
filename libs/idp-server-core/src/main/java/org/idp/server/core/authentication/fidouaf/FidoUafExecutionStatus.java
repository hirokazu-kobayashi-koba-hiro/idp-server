package org.idp.server.core.authentication.fidouaf;

public enum FidoUafExecutionStatus {
  OK,
  CLIENT_ERROR,
  SERVER_ERROR;

  public boolean isOk() {
    return this == OK;
  }

  public boolean isClientError() {
    return this == CLIENT_ERROR;
  }

  public boolean isServerError() {
    return this == SERVER_ERROR;
  }
}

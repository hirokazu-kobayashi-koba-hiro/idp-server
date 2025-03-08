package org.idp.server.core.handler.federation.io;

public enum FederationCallbackStatus {
  OK,
  BAD_REQUEST,
  SERVER_ERROR;

  public boolean isError() {
    return this == BAD_REQUEST || this == SERVER_ERROR;
  }
}

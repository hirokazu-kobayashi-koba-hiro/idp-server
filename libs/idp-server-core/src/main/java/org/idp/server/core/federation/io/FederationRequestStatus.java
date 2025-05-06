package org.idp.server.core.federation.io;

public enum FederationRequestStatus {
  REDIRECABLE_OK, BAD_REQUEST, REDIRECABLE_BAD_REQUEST, SERVER_ERROR;

  public boolean isError() {
    return this == BAD_REQUEST || this == SERVER_ERROR;
  }
}

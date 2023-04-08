package org.idp.server.io.status;

/** OAuthRequestStatus */
public enum OAuthRequestStatus {
  OK,
  NO_INTERACTION_OK,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }
}

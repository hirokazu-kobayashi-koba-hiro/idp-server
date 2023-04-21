package org.idp.server.handler.oauth.io;

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

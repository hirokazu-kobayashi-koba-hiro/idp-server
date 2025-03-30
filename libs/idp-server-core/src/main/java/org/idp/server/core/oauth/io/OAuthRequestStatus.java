package org.idp.server.core.oauth.io;

/** OAuthRequestStatus */
public enum OAuthRequestStatus {
  OK,
  OK_SESSION_ENABLE,
  NO_INTERACTION_OK,
  OK_ACCOUNT_CREATION,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }
}

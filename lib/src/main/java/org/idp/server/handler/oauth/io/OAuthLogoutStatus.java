package org.idp.server.handler.oauth.io;

/** OAuthLogoutStatus */
public enum OAuthLogoutStatus {
  OK,
  REDIRECABLE_FOUND,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }

  public boolean isRedirectableBadRequest() {
    return this == REDIRECABLE_BAD_REQUEST;
  }

  public boolean isError() {
    return this == BAD_REQUEST || this == SERVER_ERROR;
  }
}

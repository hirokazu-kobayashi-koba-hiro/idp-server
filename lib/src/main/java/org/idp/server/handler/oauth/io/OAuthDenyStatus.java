package org.idp.server.handler.oauth.io;

public enum OAuthDenyStatus {
  OK,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }

  public boolean isRedirectableBadRequest() {
    return this == REDIRECABLE_BAD_REQUEST;
  }
}

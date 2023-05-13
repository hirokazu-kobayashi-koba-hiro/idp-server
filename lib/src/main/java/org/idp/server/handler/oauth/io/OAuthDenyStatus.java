package org.idp.server.handler.oauth.io;

public enum OAuthDenyStatus {
  OK,
  BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }
}

package org.idp.server.handler.oauth.io;

/** OAuthAuthorizeStatus */
public enum OAuthAuthorizeStatus {
  OK,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }
}

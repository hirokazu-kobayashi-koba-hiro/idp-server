package org.idp.server.handler.io.status;

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
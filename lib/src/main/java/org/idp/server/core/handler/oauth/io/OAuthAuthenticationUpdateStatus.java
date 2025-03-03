package org.idp.server.core.handler.oauth.io;

/** OAuthAuthenticationUpdateStatus */
public enum OAuthAuthenticationUpdateStatus {
  OK,
  BAD_REQUEST,
  NOT_FOUND,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }

  public boolean isError() {
    return this == BAD_REQUEST || this == NOT_FOUND || this == SERVER_ERROR;
  }
}

package org.idp.server.core.oidc.io;

/** OAuthPushedRequestStatus */
public enum OAuthPushedRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  SERVER_ERROR(500);

  int statusCode;

  OAuthPushedRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public boolean isError() {
    return this == BAD_REQUEST || this == UNAUTHORIZED || this == SERVER_ERROR;
  }
}

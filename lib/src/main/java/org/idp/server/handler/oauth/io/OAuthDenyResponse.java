package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.response.AuthorizationErrorResponse;

public class OAuthDenyResponse {
  OAuthDenyStatus status;
  AuthorizationErrorResponse errorResponse;

  public OAuthDenyResponse() {}

  public OAuthDenyResponse(OAuthDenyStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
  }

  public OAuthDenyStatus status() {
    return status;
  }

  public AuthorizationErrorResponse errorResponse() {
    return errorResponse;
  }

  public String redirectUriValue() {
    return errorResponse.redirectUriValue();
  }
}

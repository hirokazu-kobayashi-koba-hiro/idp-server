package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.response.AuthorizationErrorResponse;

public class OAuthDenyResponse {
  OAuthDenyStatus status;
  AuthorizationErrorResponse errorResponse;
  String error;
  String errorDescription;

  public OAuthDenyResponse() {}

  public OAuthDenyResponse(OAuthDenyStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
  }

  public OAuthDenyResponse(OAuthDenyStatus status, String error, String errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
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

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }
}

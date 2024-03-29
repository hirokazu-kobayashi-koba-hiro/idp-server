package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationResponse;

/** OAuthAuthorizeResponse */
public class OAuthAuthorizeResponse {
  OAuthAuthorizeStatus status;
  AuthorizationResponse authorizationResponse;
  AuthorizationErrorResponse errorResponse;
  String error;
  String errorDescription;

  public OAuthAuthorizeResponse() {}

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, AuthorizationResponse authorizationResponse) {
    this.status = status;
    this.authorizationResponse = authorizationResponse;
  }

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
  }

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, String error, String errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthAuthorizeStatus status() {
    return status;
  }

  public AuthorizationResponse authorizationResponse() {
    return authorizationResponse;
  }

  public String redirectUriValue() {
    if (status.isOK()) {
      return authorizationResponse.redirectUriValue();
    }
    if (status.isRedirectableBadRequest()) {
      errorResponse.redirectUriValue();
    }
    // FIXME
    return "";
  }

  public String error() {
    return errorResponse.error().value();
  }

  public String errorDescription() {
    return errorResponse.errorDescription().value();
  }
}

package org.idp.server.core.oauth.io;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationResponse;

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

  public Map<String, Object> contents() {
    if (status.isError() && Objects.nonNull(errorResponse)) {
      return Map.of("error", error(), "error_description", errorDescription());
    }
    if (status.isError()) {
      return Map.of("error", error, "error_description", errorDescription);
    }
    return Map.of("redirect_uri", redirectUriValue());
  }
}

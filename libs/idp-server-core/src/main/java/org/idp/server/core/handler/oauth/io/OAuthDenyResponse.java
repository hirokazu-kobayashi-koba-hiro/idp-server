package org.idp.server.core.handler.oauth.io;

import java.util.Map;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;

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

  public Map<String, Object> contents() {
    if (status.isError()) {
      return Map.of("error", error, "errorDescription", errorDescription);
    }
    return Map.of("redirect_uri", redirectUriValue());
  }
}

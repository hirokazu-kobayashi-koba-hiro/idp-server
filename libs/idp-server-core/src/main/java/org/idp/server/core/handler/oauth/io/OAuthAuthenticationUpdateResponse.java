package org.idp.server.core.handler.oauth.io;

import java.util.Map;
import org.idp.server.core.oauth.response.AuthorizationResponse;

public class OAuthAuthenticationUpdateResponse {
  OAuthAuthenticationUpdateStatus status;
  AuthorizationResponse authorizationResponse;
  String error;
  String errorDescription;

  public OAuthAuthenticationUpdateResponse() {}

  public OAuthAuthenticationUpdateResponse(
      OAuthAuthenticationUpdateStatus status, AuthorizationResponse authorizationResponse) {
    this.status = status;
    this.authorizationResponse = authorizationResponse;
  }

  public OAuthAuthenticationUpdateResponse(
      OAuthAuthenticationUpdateStatus status, String error, String errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthAuthenticationUpdateStatus status() {
    return status;
  }

  public AuthorizationResponse authorizationResponse() {
    return authorizationResponse;
  }

  public Map<String, Object> contents() {
    if (status.isError()) {
      return Map.of("error", error, "error_description", errorDescription);
    }
    return Map.of();
  }
}

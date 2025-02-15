package org.idp.server.handler.oauth.io;

import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationResponse;

import java.util.Map;
import java.util.Objects;

/** OAuthLogoutResponse */
public class OAuthLogoutResponse {
  OAuthLogoutStatus status;
  String redirectUriValue;


  public OAuthLogoutResponse() {}

  public OAuthLogoutResponse(
          OAuthLogoutStatus status, String redirectUriValue) {
    this.status = status;
    this.redirectUriValue = redirectUriValue;
  }

  public Map<String, Object> contents() {
    return Map.of("redirect_uri", redirectUriValue);
  }

  public OAuthLogoutStatus status() {
    return status;
  }

  public String redirectUriValue() {
    return redirectUriValue;
  }
}

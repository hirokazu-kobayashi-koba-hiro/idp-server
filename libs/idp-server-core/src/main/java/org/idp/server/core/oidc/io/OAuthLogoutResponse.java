package org.idp.server.core.oidc.io;

import java.util.Map;

/** OAuthLogoutResponse */
public class OAuthLogoutResponse {
  OAuthLogoutStatus status;
  String redirectUriValue;

  public OAuthLogoutResponse() {}

  public OAuthLogoutResponse(OAuthLogoutStatus status, String redirectUriValue) {
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

package org.idp.server.core.federation.sso.oidc;

import java.util.Map;
import java.util.Objects;

public class OidcTokenResponse {

  Map<String, Object> values;

  public OidcTokenResponse() {}

  public OidcTokenResponse(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }

  public String accessToken() {
    return (String) values.get("access_token");
  }

  public String refreshToken() {
    return (String) values.get("refresh_token");
  }

  public String idToken() {
    return (String) values.get("id_token");
  }

  public String expiresIn() {
    return (String) values.get("expires_in");
  }

  public String scope() {
    return (String) values.get("scope");
  }

  public String tokenType() {
    return (String) values.get("token_type");
  }

  public boolean hasAccessToken() {
    return values.containsKey("access_token");
  }

  public boolean hasRefreshToken() {
    return values.containsKey("refresh_token");
  }

  public boolean hasIdToken() {
    return values.containsKey("id_token");
  }

  public boolean hasExpiresIn() {
    return values.containsKey("expires_in");
  }

  public boolean hasScope() {
    return values.containsKey("scope");
  }

  public boolean hasTokenType() {
    return values.containsKey("token_type");
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }
}

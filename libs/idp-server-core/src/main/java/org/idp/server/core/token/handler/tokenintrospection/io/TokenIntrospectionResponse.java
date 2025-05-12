package org.idp.server.core.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.token.OAuthToken;

public class TokenIntrospectionResponse {
  TokenIntrospectionRequestStatus status;
  OAuthToken oAuthToken;
  Map<String, Object> response;

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status, OAuthToken oAuthToken, Map<String, Object> contents) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.response = contents;
  }

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.response = contents;
  }

  public TokenIntrospectionRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isActive() {
    return Boolean.TRUE.equals(response.get("active"));
  }

  public String subject() {
    return (String) response.getOrDefault("sub", "");
  }

  public boolean isExpired() {
    return status.isExpired();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public boolean hasOAuthToken() {
    return oAuthToken != null && oAuthToken.exists();
  }

  public boolean isClientCredentialsGrant() {
    return oAuthToken.isClientCredentialsGrant();
  }

  public DefaultSecurityEventType securityEventType() {
    if (isExpired()) {
      return DefaultSecurityEventType.inspect_token_failure;
    }

    if (!isOK()) {
      return DefaultSecurityEventType.inspect_token_failure;
    }

    return DefaultSecurityEventType.inspect_token_success;
  }
}

package org.idp.server.core.token.handler.tokenrevocation.io;

import java.util.Map;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class TokenRevocationResponse {
  TokenRevocationRequestStatus status;
  OAuthToken oAuthToken;
  Map<String, Object> response;

  public TokenRevocationResponse(
      TokenRevocationRequestStatus status, OAuthToken oAuthToken, Map<String, Object> contents) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.response = contents;
  }

  public TokenRevocationRequestStatus status() {
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

  public boolean isOK() {
    return status.isOK();
  }

  public DefaultSecurityEventType securityEventType() {
    if (!isOK()) {
      return DefaultSecurityEventType.revoke_token_failure;
    }

    return DefaultSecurityEventType.revoke_token_success;
  }

  public boolean hasOAuthToken() {
    return oAuthToken != null && oAuthToken.exists();
  }
}

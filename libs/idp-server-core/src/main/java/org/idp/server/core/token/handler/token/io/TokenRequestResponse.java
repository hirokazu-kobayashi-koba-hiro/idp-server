package org.idp.server.core.token.handler.token.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenErrorResponse;
import org.idp.server.core.token.TokenResponse;
import org.idp.server.core.token.TokenResponseBuilder;

public class TokenRequestResponse {
  TokenRequestStatus status;
  OAuthToken oAuthToken;
  TokenResponse tokenResponse;
  TokenErrorResponse errorResponse;
  Map<String, String> headers;

  public TokenRequestResponse(TokenRequestStatus status, OAuthToken oAuthToken) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.tokenResponse =
        new TokenResponseBuilder()
            .add(oAuthToken.accessTokenEntity())
            .add(oAuthToken.refreshTokenEntity())
            .add(oAuthToken.scopes())
            .add(oAuthToken.tokenType())
            .add(oAuthToken.expiresIn())
            .add(oAuthToken.idToken())
            .add(oAuthToken.authorizationDetails())
            .add(oAuthToken.cNonce())
            .add(oAuthToken.cNonceExpiresIn())
            .build();
    this.errorResponse = new TokenErrorResponse();
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public TokenRequestResponse(TokenRequestStatus status, TokenErrorResponse errorResponse) {
    this.status = status;
    this.tokenResponse = new TokenResponseBuilder().build();
    this.errorResponse = errorResponse;
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public String contents() {
    if (status.isOK()) {
      return tokenResponse.contents();
    }
    return errorResponse.contents();
  }

  public Map<String, String> responseHeaders() {
    return headers;
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public TokenResponse tokenResponse() {
    return tokenResponse;
  }

  public TokenErrorResponse errorResponse() {
    return errorResponse;
  }

  public TokenRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public boolean hasIdToken() {
    return oAuthToken.hasIdToken();
  }

  public DefaultSecurityEventType securityEventType(TokenRequest tokenRequest) {
    if (!isOK()) {
      return DefaultSecurityEventType.issue_token_failure;
    }

    if (hasIdToken()) {
      return DefaultSecurityEventType.login_success;
    }

    if (tokenRequest.isRefreshTokenGrant()) {
      return DefaultSecurityEventType.refresh_token_success;
    }

    return DefaultSecurityEventType.issue_token_success;
  }
}

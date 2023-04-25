package org.idp.server.handler.token.io;

import java.util.Map;
import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.TokenResponse;

public class TokenRequestResponse {
  TokenRequestStatus status;
  TokenResponse tokenResponse;
  TokenErrorResponse errorResponse;

  public TokenRequestResponse(TokenRequestStatus status, TokenResponse tokenResponse) {
    this.status = status;
    this.tokenResponse = tokenResponse;
    this.errorResponse = new TokenErrorResponse();
  }

  public TokenRequestResponse(TokenRequestStatus status, TokenErrorResponse errorResponse) {
    this.status = status;
    this.tokenResponse = new TokenResponse();
    this.errorResponse = errorResponse;
  }

  public Map<String, Object> response() {
    if (status.isOK()) {
      return tokenResponse.response();
    }
    return errorResponse.response();
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
}

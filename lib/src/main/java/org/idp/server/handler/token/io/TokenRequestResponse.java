package org.idp.server.handler.token.io;

import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.TokenResponse;
import org.idp.server.token.TokenResponseBuilder;

import java.util.HashMap;
import java.util.Map;

public class TokenRequestResponse {
  TokenRequestStatus status;
  TokenResponse tokenResponse;
  TokenErrorResponse errorResponse;
  Map<String, String> headers;

  public TokenRequestResponse(TokenRequestStatus status, TokenResponse tokenResponse) {
    this.status = status;
    this.tokenResponse = tokenResponse;
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

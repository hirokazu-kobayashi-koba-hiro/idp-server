package org.idp.server.io;

import java.util.Map;
import org.idp.server.io.status.TokenRequestStatus;
import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.TokenResponse;

public class TokenRequestResponse {
  TokenRequestStatus status;
  TokenResponse response;
  TokenErrorResponse errorResponse;

  public TokenRequestResponse(TokenRequestStatus status, TokenResponse response) {
    this.status = status;
    this.response = response;
    this.errorResponse = new TokenErrorResponse();
  }

  public TokenRequestResponse(TokenRequestStatus status, TokenErrorResponse errorResponse) {
    this.status = status;
    this.response = new TokenResponse();
    this.errorResponse = errorResponse;
  }

  public Map<String, Object> response() {
    if (status.isOK()) {
      return response.response();
    }
    return errorResponse.response();
  }

  public TokenRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }
}

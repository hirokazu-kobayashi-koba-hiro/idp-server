package org.idp.server.handler.token.io;

import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.TokenResponse;
import org.idp.server.token.TokenResponseBuilder;
import org.idp.server.type.ContentType;

public class TokenRequestResponse {
  TokenRequestStatus status;
  TokenResponse tokenResponse;
  TokenErrorResponse errorResponse;
  ContentType contentType;

  public TokenRequestResponse(TokenRequestStatus status, TokenResponse tokenResponse) {
    this.status = status;
    this.tokenResponse = tokenResponse;
    this.errorResponse = new TokenErrorResponse();
    this.contentType = ContentType.application_json;
  }

  public TokenRequestResponse(TokenRequestStatus status, TokenErrorResponse errorResponse) {
    this.status = status;
    this.tokenResponse = new TokenResponseBuilder().build();
    this.errorResponse = errorResponse;
    this.contentType = ContentType.application_json;
  }

  public String contents() {
    if (status.isOK()) {
      return tokenResponse.contents();
    }
    return errorResponse.contents();
  }

  public ContentType contentType() {
    return contentType;
  }

  public String contentTypeValue() {
    return contentType.value();
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

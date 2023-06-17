package org.idp.server.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.token.OAuthToken;

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
}

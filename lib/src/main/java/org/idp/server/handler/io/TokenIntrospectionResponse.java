package org.idp.server.handler.io;

import java.util.Map;
import org.idp.server.handler.io.status.TokenIntrospectionRequestStatus;
import org.idp.server.token.AccessTokenPayload;

public class TokenIntrospectionResponse {
  TokenIntrospectionRequestStatus status;
  AccessTokenPayload accessTokenPayload;
  Map<String, Object> response;

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status,
      AccessTokenPayload accessTokenPayload,
      Map<String, Object> contents) {
    this.status = status;
    this.accessTokenPayload = accessTokenPayload;
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

  public AccessTokenPayload accessTokenPayload() {
    return accessTokenPayload;
  }

  public Map<String, Object> response() {
    return response;
  }
}

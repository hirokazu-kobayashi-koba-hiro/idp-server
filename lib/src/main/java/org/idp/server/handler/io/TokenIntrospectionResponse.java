package org.idp.server.handler.io;

import java.util.Map;
import org.idp.server.handler.io.status.TokenIntrospectionRequestStatus;
import org.idp.server.token.AccessTokenPayload;

public class TokenIntrospectionResponse {
  TokenIntrospectionRequestStatus status;
  AccessTokenPayload accessTokenPayload;
  Map<String, Object> contents;

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status,
      AccessTokenPayload accessTokenPayload,
      Map<String, Object> contents) {
    this.status = status;
    this.accessTokenPayload = accessTokenPayload;
    this.contents = contents;
  }

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }
}

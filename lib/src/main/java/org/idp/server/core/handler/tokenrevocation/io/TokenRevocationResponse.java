package org.idp.server.core.handler.tokenrevocation.io;

import java.util.Map;

public class TokenRevocationResponse {
  TokenRevocationRequestStatus status;
  Map<String, Object> response;

  public TokenRevocationResponse(
      TokenRevocationRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.response = contents;
  }

  public TokenRevocationRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> response() {
    return response;
  }
}

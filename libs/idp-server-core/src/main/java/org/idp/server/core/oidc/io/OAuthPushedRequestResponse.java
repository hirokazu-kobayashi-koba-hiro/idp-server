package org.idp.server.core.oidc.io;

import java.util.Map;

public class OAuthPushedRequestResponse {
  OAuthPushedRequestStatus status;
  Map<String, Object> contents;

  public OAuthPushedRequestResponse() {}

  public OAuthPushedRequestResponse(OAuthPushedRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public OAuthPushedRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}

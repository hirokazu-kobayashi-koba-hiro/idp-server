package org.idp.server.core.discovery.handler.io;

import java.util.Map;

public class JwksRequestResponse {
  JwksRequestStatus status;
  Map<String, Object> content;

  public JwksRequestResponse(JwksRequestStatus status, Map<String, Object> content) {
    this.status = status;
    this.content = content;
  }

  public JwksRequestStatus status() {
    return status;
  }

  public Map<String, Object> content() {
    return content;
  }

  public int statusCode() {
    return status.statusCode();
  }
}

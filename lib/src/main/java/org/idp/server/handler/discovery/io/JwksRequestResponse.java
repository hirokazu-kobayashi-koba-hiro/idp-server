package org.idp.server.handler.discovery.io;

import java.util.Map;

public class JwksRequestResponse {
  JwksRequestStatus status;
  Map<String, Object> content;

  public JwksRequestResponse(JwksRequestStatus status, Map<String, Object> content) {
    this.status = status;
    this.content = content;
  }

  public Map<String, Object> content() {
    return content;
  }

  public int statusCode() {
    return status.statusCode();
  }
}

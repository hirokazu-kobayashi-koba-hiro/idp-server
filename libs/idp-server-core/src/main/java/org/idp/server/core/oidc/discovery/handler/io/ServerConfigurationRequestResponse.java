package org.idp.server.core.oidc.discovery.handler.io;

import java.util.Map;

public class ServerConfigurationRequestResponse {

  ServerConfigurationRequestStatus status;
  Map<String, Object> content;

  public ServerConfigurationRequestResponse(
      ServerConfigurationRequestStatus status, Map<String, Object> content) {
    this.status = status;
    this.content = content;
  }

  public ServerConfigurationRequestStatus status() {
    return status;
  }

  public Map<String, Object> content() {
    return content;
  }

  public int statusCode() {
    return status.statusCode();
  }
}

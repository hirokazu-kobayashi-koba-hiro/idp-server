package org.idp.server.core.oidc.configuration.handler.io;

import java.util.Map;

public class ClientConfigurationManagementResponse {

  ClientConfigurationManagementStatus status;

  Map<String, Object> content;

  public ClientConfigurationManagementResponse(ClientConfigurationManagementStatus status, Map<String, Object> content) {
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

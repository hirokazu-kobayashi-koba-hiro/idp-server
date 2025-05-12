package org.idp.server.control_plane.management.oidc.client.io;

import java.util.Map;

public class ClientConfigurationManagementResponse {
  ClientManagementStatus status;
  Map<String, Object> contents;

  public ClientConfigurationManagementResponse(
      ClientManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public ClientManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}

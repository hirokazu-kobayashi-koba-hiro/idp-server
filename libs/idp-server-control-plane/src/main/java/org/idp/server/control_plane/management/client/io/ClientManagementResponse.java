package org.idp.server.control_plane.management.client.io;

import java.util.Map;

public class ClientManagementResponse {
  ClientManagementStatus status;
  Map<String, Object> contents;

  public ClientManagementResponse(ClientManagementStatus status, Map<String, Object> contents) {
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

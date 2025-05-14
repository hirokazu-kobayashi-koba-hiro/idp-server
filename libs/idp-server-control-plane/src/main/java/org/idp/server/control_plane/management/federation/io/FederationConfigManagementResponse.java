package org.idp.server.control_plane.management.federation.io;

import java.util.Map;

public class FederationConfigManagementResponse {
  FederationConfigManagementStatus status;
  Map<String, Object> contents;

  public FederationConfigManagementResponse(
      FederationConfigManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public FederationConfigManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}

package org.idp.server.control_plane.tenant.io;

import java.util.Map;

public class TenantInitializationResponse {

  TenantInitializationStatus status;
  Map<String, Object> contents;

  public TenantInitializationResponse(
      TenantInitializationStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public TenantInitializationStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isOk() {
    return this.status.isOk();
  }
}

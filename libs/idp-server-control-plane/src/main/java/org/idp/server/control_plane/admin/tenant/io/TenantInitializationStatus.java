package org.idp.server.control_plane.admin.tenant.io;

public enum TenantInitializationStatus {
  OK(200),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  SERVER_ERROR(500);

  int statusCode;

  TenantInitializationStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

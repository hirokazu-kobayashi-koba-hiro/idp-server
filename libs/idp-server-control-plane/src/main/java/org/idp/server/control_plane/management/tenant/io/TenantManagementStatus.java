package org.idp.server.control_plane.management.tenant.io;

public enum TenantManagementStatus {
  OK(200),
  CREATED(201),
  NO_CONTENT(204),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  SERVER_ERROR(500);

  int statusCode;

  TenantManagementStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

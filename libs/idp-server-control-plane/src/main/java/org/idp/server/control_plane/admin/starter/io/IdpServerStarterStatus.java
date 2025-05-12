package org.idp.server.control_plane.admin.starter.io;

public enum IdpServerStarterStatus {
  OK(200),
  CREATED(201),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  SERVER_ERROR(500);

  int statusCode;

  IdpServerStarterStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

package org.idp.server.control_plane.management.oidc.client.io;

public enum ClientManagementStatus {
  OK(200),
  CREATED(201),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  SERVER_ERROR(500);

  int statusCode;

  ClientManagementStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

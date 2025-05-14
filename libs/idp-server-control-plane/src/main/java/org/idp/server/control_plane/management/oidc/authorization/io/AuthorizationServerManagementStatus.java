package org.idp.server.control_plane.management.oidc.authorization.io;

public enum AuthorizationServerManagementStatus {
  OK(200),
  CREATED(201),
  NO_CONTENT(204),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  SERVER_ERROR(500);

  int statusCode;

  AuthorizationServerManagementStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

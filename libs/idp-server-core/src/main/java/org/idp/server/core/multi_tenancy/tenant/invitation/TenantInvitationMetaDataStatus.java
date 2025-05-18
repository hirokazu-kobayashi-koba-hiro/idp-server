package org.idp.server.core.multi_tenancy.tenant.invitation;

public enum TenantInvitationMetaDataStatus {
  OK(200),
  CREATED(201),
  INVALID_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  SERVER_ERROR(500);

  int statusCode;

  TenantInvitationMetaDataStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }
}

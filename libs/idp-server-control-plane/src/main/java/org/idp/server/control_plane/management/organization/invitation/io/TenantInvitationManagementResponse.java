package org.idp.server.control_plane.management.organization.invitation.io;

import java.util.Map;

public class TenantInvitationManagementResponse {

  TenantInvitationManagementStatus status;
  Map<String, Object> contents;

  public TenantInvitationManagementResponse(
      TenantInvitationManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public TenantInvitationManagementStatus status() {
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

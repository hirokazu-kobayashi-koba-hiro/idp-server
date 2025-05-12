package org.idp.server.control_plane.management.identity.verification.io;

import java.util.Map;

public class IdentityVerificationConfigManagementResponse {
  IdentityVerificationConfigManagementStatus status;
  Map<String, Object> contents;

  public IdentityVerificationConfigManagementResponse(
      IdentityVerificationConfigManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public IdentityVerificationConfigManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}

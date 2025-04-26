package org.idp.server.core.identity.trustframework;

import java.util.Map;

public class IdentityVerificationApplicationResponse {

  IdentityVerificationApplicationStatus status;
  Map<String, Object> response;

  public static IdentityVerificationApplicationResponse OK(Map<String, Object> response) {
    return new IdentityVerificationApplicationResponse(
        IdentityVerificationApplicationStatus.OK, response);
  }

  public static IdentityVerificationApplicationResponse CLIENT_ERROR(Map<String, Object> response) {
    return new IdentityVerificationApplicationResponse(
        IdentityVerificationApplicationStatus.CLIENT_ERROR, response);
  }

  private IdentityVerificationApplicationResponse(
      IdentityVerificationApplicationStatus status, Map<String, Object> response) {
    this.status = status;
    this.response = response;
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isOK() {
    return status == IdentityVerificationApplicationStatus.OK;
  }

  public int statusCode() {
    return status.statusCode();
  }
}

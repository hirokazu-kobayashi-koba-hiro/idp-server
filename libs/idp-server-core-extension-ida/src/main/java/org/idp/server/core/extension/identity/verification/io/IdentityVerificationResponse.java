package org.idp.server.core.extension.identity.verification.io;

import java.util.Map;

public class IdentityVerificationResponse {

  IdentityVerificationApplicationStatus status;
  Map<String, Object> response;

  public static IdentityVerificationResponse OK(Map<String, Object> response) {
    return new IdentityVerificationResponse(IdentityVerificationApplicationStatus.OK, response);
  }

  public static IdentityVerificationResponse CLIENT_ERROR(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        IdentityVerificationApplicationStatus.CLIENT_ERROR, response);
  }

  public static IdentityVerificationResponse SERVER_ERROR(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        IdentityVerificationApplicationStatus.SERVER_ERROR, response);
  }

  private IdentityVerificationResponse(
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

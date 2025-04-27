package org.idp.server.core.identity.trustframework.application;

import java.util.Map;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus;

public class IdentityVerificationResponse {

  org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus status;
  Map<String, Object> response;

  public static IdentityVerificationResponse OK(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus.OK,
        response);
  }

  public static IdentityVerificationResponse CLIENT_ERROR(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus
            .CLIENT_ERROR,
        response);
  }

  private IdentityVerificationResponse(
      org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus status,
      Map<String, Object> response) {
    this.status = status;
    this.response = response;
  }

  public org.idp.server.core.identity.trustframework.IdentityVerificationApplicationStatus
      status() {
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

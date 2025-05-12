package org.idp.server.control_plane.management.identity.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.io.UserManagementStatus;

public class UserRegistrationVerificationResult {
  boolean isValid;
  VerificationResult userVerificationResult;
  boolean dryRun;

  public static UserRegistrationVerificationResult success(
      VerificationResult userVerificationResult, boolean dryRun) {
    return new UserRegistrationVerificationResult(true, userVerificationResult, dryRun);
  }

  public static UserRegistrationVerificationResult error(
      VerificationResult userVerificationResult, boolean dryRun) {
    return new UserRegistrationVerificationResult(false, userVerificationResult, dryRun);
  }

  private UserRegistrationVerificationResult(
      boolean isValid, VerificationResult userVerificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.userVerificationResult = userVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public UserManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "user registration verification is failed");
    Map<String, Object> details = new HashMap<>();
    if (!userVerificationResult.isValid()) {
      details.put("user", userVerificationResult.errors());
    }
    response.put("details", details);
    return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
  }
}

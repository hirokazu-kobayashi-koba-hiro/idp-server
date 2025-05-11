package org.idp.server.control_plane.starter.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.starter.io.IdpServerStarterStatus;
import org.idp.server.control_plane.verifier.VerificationResult;

public class IdpServerVerificationResult {
  boolean isValid;
  VerificationResult tenantVerificationResult;
  boolean dryRun;

  public static IdpServerVerificationResult success(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new IdpServerVerificationResult(true, tenantVerificationResult, dryRun);
  }

  public static IdpServerVerificationResult error(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new IdpServerVerificationResult(false, tenantVerificationResult, dryRun);
  }

  private IdpServerVerificationResult(
      boolean isValid, VerificationResult tenantVerificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.tenantVerificationResult = tenantVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public IdpServerStarterResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter verification is failed");
    Map<String, Object> details = new HashMap<>();
    if (!tenantVerificationResult.isValid()) {
      details.put("tenant", tenantVerificationResult.errors());
    }
    response.put("details", details);
    return new IdpServerStarterResponse(IdpServerStarterStatus.INVALID_REQUEST, response);
  }
}

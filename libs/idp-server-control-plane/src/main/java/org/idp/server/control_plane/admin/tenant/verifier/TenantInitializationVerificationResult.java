/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.admin.tenant.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationResponse;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationStatus;
import org.idp.server.control_plane.base.verifier.VerificationResult;

public class TenantInitializationVerificationResult {
  boolean isValid;
  VerificationResult tenantVerificationResult;
  VerificationResult clientVerificationResult;
  boolean dryRun;

  public static TenantInitializationVerificationResult success(
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    return new TenantInitializationVerificationResult(
        true, tenantVerificationResult, clientVerificationResult, dryRun);
  }

  public static TenantInitializationVerificationResult error(
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    return new TenantInitializationVerificationResult(
        false, tenantVerificationResult, clientVerificationResult, dryRun);
  }

  private TenantInitializationVerificationResult(
      boolean isValid,
      VerificationResult tenantVerificationResult,
      VerificationResult clientVerificationResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.tenantVerificationResult = tenantVerificationResult;
    this.clientVerificationResult = clientVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public TenantInitializationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter verification is failed");
    Map<String, Object> details = new HashMap<>();

    if (!tenantVerificationResult.isValid()) {
      details.put("tenant", tenantVerificationResult.errors());
    }
    if (!clientVerificationResult.isValid()) {
      details.put("client", clientVerificationResult.errors());
    }

    response.put("details", details);
    return new TenantInitializationResponse(TenantInitializationStatus.INVALID_REQUEST, response);
  }
}

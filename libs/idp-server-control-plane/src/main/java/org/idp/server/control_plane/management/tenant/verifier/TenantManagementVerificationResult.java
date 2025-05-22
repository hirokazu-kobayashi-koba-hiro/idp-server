/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;

public class TenantManagementVerificationResult {
  boolean isValid;
  VerificationResult tenantVerificationResult;
  boolean dryRun;

  public static TenantManagementVerificationResult success(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new TenantManagementVerificationResult(true, tenantVerificationResult, dryRun);
  }

  public static TenantManagementVerificationResult error(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new TenantManagementVerificationResult(false, tenantVerificationResult, dryRun);
  }

  private TenantManagementVerificationResult(
      boolean isValid, VerificationResult tenantVerificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.tenantVerificationResult = tenantVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public TenantManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter verification is failed");
    Map<String, Object> details = new HashMap<>();
    if (!tenantVerificationResult.isValid()) {
      details.put("tenant", tenantVerificationResult.errors());
    }
    response.put("details", details);
    return new TenantManagementResponse(TenantManagementStatus.INVALID_REQUEST, response);
  }
}

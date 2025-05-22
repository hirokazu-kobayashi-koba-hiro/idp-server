/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.onboarding.verifier;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;

public class OnboardingVerificationResult {
  boolean isValid;
  VerificationResult tenantVerificationResult;
  boolean dryRun;

  public static OnboardingVerificationResult success(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new OnboardingVerificationResult(true, tenantVerificationResult, dryRun);
  }

  public static OnboardingVerificationResult error(
      VerificationResult tenantVerificationResult, boolean dryRun) {
    return new OnboardingVerificationResult(false, tenantVerificationResult, dryRun);
  }

  private OnboardingVerificationResult(
      boolean isValid, VerificationResult tenantVerificationResult, boolean dryRun) {
    this.isValid = isValid;
    this.tenantVerificationResult = tenantVerificationResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public OnboardingResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter verification is failed");
    Map<String, Object> details = new HashMap<>();
    if (!tenantVerificationResult.isValid()) {
      details.put("tenant", tenantVerificationResult.errors());
    }
    response.put("details", details);
    return new OnboardingResponse(OnboardingStatus.INVALID_REQUEST, response);
  }
}

/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.onboarding.verifier;

import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.onboarding.OnboardingContext;

public class OnboardingVerifier {

  TenantVerifier tenantVerifier;

  public OnboardingVerifier(TenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public OnboardingVerificationResult verify(OnboardingContext context) {

    VerificationResult verificationResult = tenantVerifier.verify(context.tenant());

    if (!verificationResult.isValid()) {
      return OnboardingVerificationResult.error(verificationResult, context.isDryRun());
    }

    return OnboardingVerificationResult.success(verificationResult, context.isDryRun());
  }
}

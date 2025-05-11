package org.idp.server.control_plane.onboarding.verifier;

import org.idp.server.control_plane.onboarding.OnboardingContext;
import org.idp.server.control_plane.verifier.TenantVerifier;
import org.idp.server.control_plane.verifier.VerificationResult;

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

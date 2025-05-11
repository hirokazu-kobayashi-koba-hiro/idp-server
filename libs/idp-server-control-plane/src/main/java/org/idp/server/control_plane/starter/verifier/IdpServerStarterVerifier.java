package org.idp.server.control_plane.starter.verifier;

import org.idp.server.control_plane.starter.IdpServerStarterContext;
import org.idp.server.control_plane.verifier.VerificationResult;

public class IdpServerStarterVerifier {

  StarterTenantVerifier tenantVerifier;

  public IdpServerStarterVerifier(StarterTenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public IdpServerVerificationResult verify(IdpServerStarterContext context) {

    VerificationResult verificationResult = tenantVerifier.verify(context.tenant());

    if (!verificationResult.isValid()) {
      return IdpServerVerificationResult.error(verificationResult, context.isDryRun());
    }

    return IdpServerVerificationResult.success(verificationResult, context.isDryRun());
  }
}

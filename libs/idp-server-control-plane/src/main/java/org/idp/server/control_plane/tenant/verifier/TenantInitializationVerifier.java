package org.idp.server.control_plane.tenant.verifier;

import org.idp.server.control_plane.tenant.TenantInitializationContext;
import org.idp.server.control_plane.verifier.ClientVerifier;
import org.idp.server.control_plane.verifier.TenantVerifier;
import org.idp.server.control_plane.verifier.VerificationResult;

public class TenantInitializationVerifier {

  TenantVerifier tenantVerifier;
  ClientVerifier clientVerifier;

  public TenantInitializationVerifier(
      TenantVerifier tenantVerifier, ClientVerifier clientVerifier) {
    this.tenantVerifier = tenantVerifier;
    this.clientVerifier = clientVerifier;
  }

  public TenantInitializationVerificationResult verify(TenantInitializationContext context) {

    VerificationResult tenantVerificationResult = tenantVerifier.verify(context.tenant());
    VerificationResult clientVerificationResult =
        clientVerifier.verify(context.tenant(), context.clientConfiguration());

    if (!tenantVerificationResult.isValid() || !clientVerificationResult.isValid()) {
      return TenantInitializationVerificationResult.error(
          tenantVerificationResult, clientVerificationResult, context.isDryRun());
    }

    return TenantInitializationVerificationResult.success(
        tenantVerificationResult, clientVerificationResult, context.isDryRun());
  }
}

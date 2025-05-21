package org.idp.server.control_plane.management.tenant.verifier;

import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.tenant.TenantManagementRegistrationContext;

public class TenantManagementVerifier {

  TenantVerifier tenantVerifier;

  public TenantManagementVerifier(TenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public TenantManagementVerificationResult verify(TenantManagementRegistrationContext context) {

    VerificationResult verificationResult = tenantVerifier.verify(context.newTenant());

    if (!verificationResult.isValid()) {
      return TenantManagementVerificationResult.error(verificationResult, context.isDryRun());
    }

    return TenantManagementVerificationResult.success(verificationResult, context.isDryRun());
  }
}

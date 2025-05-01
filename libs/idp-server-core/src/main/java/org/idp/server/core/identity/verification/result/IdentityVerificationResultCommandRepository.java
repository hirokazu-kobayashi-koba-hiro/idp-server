package org.idp.server.core.identity.verification.result;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationResultCommandRepository {

  void register(Tenant tenant, IdentityVerificationResult result);
}

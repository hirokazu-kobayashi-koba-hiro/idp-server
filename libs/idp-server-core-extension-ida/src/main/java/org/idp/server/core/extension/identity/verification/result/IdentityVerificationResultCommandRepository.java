package org.idp.server.core.extension.identity.verification.result;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationResultCommandRepository {

  void register(Tenant tenant, IdentityVerificationResult result);
}

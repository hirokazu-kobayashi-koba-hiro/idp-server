package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationResultCommandSqlExecutor {
  void insert(Tenant tenant, IdentityVerificationResult result);
}

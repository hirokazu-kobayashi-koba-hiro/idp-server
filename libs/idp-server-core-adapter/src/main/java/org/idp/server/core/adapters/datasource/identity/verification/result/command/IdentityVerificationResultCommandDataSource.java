package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import org.idp.server.core.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationResultCommandDataSource
    implements IdentityVerificationResultCommandRepository {

  IdentityVerificationResultCommandSqlExecutors executors;

  public IdentityVerificationResultCommandDataSource() {
    this.executors = new IdentityVerificationResultCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, IdentityVerificationResult result) {
    IdentityVerificationResultCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, result);
  }
}

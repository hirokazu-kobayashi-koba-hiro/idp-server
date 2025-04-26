package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationCommandDataSource
    implements IdentityVerificationApplicationCommandRepository {

  IdentityVerificationApplicationCommandSqlExecutors executors;

  public IdentityVerificationApplicationCommandDataSource() {
    this.executors = new IdentityVerificationApplicationCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, IdentityVerificationApplication application) {

    IdentityVerificationApplicationCommandSqlExecutor executor =
        executors.get(tenant.databaseType());
    executor.insert(tenant, application);
  }

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {}
}

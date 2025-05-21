package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
  public void update(Tenant tenant, IdentityVerificationApplication application) {
    IdentityVerificationApplicationCommandSqlExecutor executor =
        executors.get(tenant.databaseType());
    executor.update(tenant, application);
  }

  @Override
  public void delete(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    IdentityVerificationApplicationCommandSqlExecutor executor =
        executors.get(tenant.databaseType());
    executor.delete(tenant, user, identifier);
  }
}

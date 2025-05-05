package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UserCommandDataSource implements UserCommandRepository {

  UserCommandSqlExecutors executors;

  public UserCommandDataSource() {
    this.executors = new UserCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, User user) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, user);
  }

  @Override
  public void update(Tenant tenant, User user) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, user);
  }

  @Override
  public void delete(Tenant tenant, UserIdentifier userIdentifier) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, userIdentifier);
  }
}

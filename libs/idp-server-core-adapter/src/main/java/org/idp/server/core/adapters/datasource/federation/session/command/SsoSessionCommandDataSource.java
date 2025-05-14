package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.sso.SsoSessionCommandRepository;
import org.idp.server.core.federation.sso.SsoSessionIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class SsoSessionCommandDataSource implements SsoSessionCommandRepository {

  SsoSessionCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public SsoSessionCommandDataSource() {
    this.executors = new SsoSessionCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> void register(Tenant tenant, SsoSessionIdentifier identifier, T payload) {
    SsoSessionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(identifier, payload);
  }

  @Override
  public void delete(Tenant tenant, SsoSessionIdentifier identifier) {
    SsoSessionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(identifier);
  }
}

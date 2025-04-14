package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.federation.SsoSessionCommandRepository;
import org.idp.server.core.federation.SsoSessionIdentifier;
import org.idp.server.core.tenant.Tenant;

public class SsoSessionCommandDataSource implements SsoSessionCommandRepository {

  SsoSessionCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public SsoSessionCommandDataSource() {
    this.executors = new SsoSessionCommandSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
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

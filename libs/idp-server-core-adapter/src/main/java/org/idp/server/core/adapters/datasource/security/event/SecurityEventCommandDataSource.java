package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SecurityEventCommandDataSource implements SecurityEventCommandRepository {

  SecurityEventSqlExecutors executors;
  JsonConverter converter;

  public SecurityEventCommandDataSource() {
    this.executors = new SecurityEventSqlExecutors();
    this.converter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, SecurityEvent securityEvent) {
    SecurityEventSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(securityEvent);
  }
}

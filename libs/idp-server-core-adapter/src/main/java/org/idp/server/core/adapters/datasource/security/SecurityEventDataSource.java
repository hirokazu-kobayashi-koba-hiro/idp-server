package org.idp.server.core.adapters.datasource.security;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.event.SecurityEventSearchCriteria;
import org.idp.server.core.tenant.Tenant;

public class SecurityEventDataSource implements SecurityEventRepository {

  SecurityEventSqlExecutors executors;
  JsonConverter converter;

  public SecurityEventDataSource() {
    this.executors = new SecurityEventSqlExecutors();
    this.converter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, SecurityEvent securityEvent) {
    SecurityEventSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(securityEvent);
  }

  @Override
  public SecurityEvents findBy(Tenant tenant, String eventServerId, String userId) {
    return null;
  }

  @Override
  public SecurityEvents search(Tenant tenant, String eventServerId, SecurityEventSearchCriteria criteria) {
    return null;
  }
}

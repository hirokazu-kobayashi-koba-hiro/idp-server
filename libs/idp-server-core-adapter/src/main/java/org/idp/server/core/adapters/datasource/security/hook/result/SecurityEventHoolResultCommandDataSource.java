package org.idp.server.core.adapters.datasource.security.hook.result;

import java.util.List;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.hook.SecurityEventHookResult;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SecurityEventHoolResultCommandDataSource
    implements SecurityEventHookResultCommandRepository {

  SecurityEventHoolResultSqlExecutors executors;

  public SecurityEventHoolResultCommandDataSource() {
    this.executors = new SecurityEventHoolResultSqlExecutors();
  }

  @Override
  public void register(
      Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results) {
    SecurityEventHoolResultSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, securityEvent, results);
  }
}

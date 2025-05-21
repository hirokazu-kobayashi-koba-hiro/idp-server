package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookConfigSqlExecutor {
  List<Map<String, String>> selectListBy(Tenant tenant);

  Map<String, String> selectOne(Tenant tenant, SecurityEventHookConfigurationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}

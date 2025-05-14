package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationIdentifier;

import java.util.List;
import java.util.Map;

public interface SecurityEventHookConfigSqlExecutor {
   List<Map<String, String>> selectListBy(Tenant tenant);

  Map<String, String> selectOne(Tenant tenant, SecurityEventHookConfigurationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}

package org.idp.server.core.adapters.datasource.authentication.config;

import org.idp.server.core.tenant.Tenant;

import java.util.Map;

public interface AuthenticationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, String key);
}

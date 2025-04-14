package org.idp.server.core.adapters.datasource.authentication.config;

import java.util.Map;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, String key);
}

package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, String key);

  Map<String, String> selectOne(Tenant tenant, AuthenticationConfigurationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}

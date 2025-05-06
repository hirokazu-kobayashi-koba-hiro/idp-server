package org.idp.server.core.adapters.datasource.federation.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements FederationConfigurationQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, FederationType federationType, SsoProvider ssoProvider) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = """
        SELECT id, payload FROM federation_configurations
        WHERE tenant_id = ?
        AND type = ?
        AND sso_provider_name = ?
        """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(federationType.name());
    params.add(ssoProvider.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

package org.idp.server.core.adapters.datasource.identity.trustframework.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationConfigSqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT id, payload
            FROM identity_verification_configurations
            WHERE tenant_id = ?
            AND type = ?
            AND enabled = true
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

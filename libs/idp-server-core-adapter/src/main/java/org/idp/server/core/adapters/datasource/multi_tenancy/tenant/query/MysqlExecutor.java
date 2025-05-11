package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class MysqlExecutor implements TenantQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, name, type, domain, attributes
            FROM tenant
            WHERE id = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAdmin() {

    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, name, type, domain, attributes
            FROM tenant
            WHERE type = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add("ADMIN");

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

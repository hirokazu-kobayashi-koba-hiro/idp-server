package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.*;

public class PostgresqlExecutor implements TenantQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = selectSql + " " + """
            WHERE id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAdmin() {

    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = selectSql + " " + """
            WHERE type = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add("ADMIN");

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
          SELECT
            id,
            name,
            type,
            domain,
            authorization_provider,
            database_type,
            attributes
            FROM tenant
          """;
}

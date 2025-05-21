package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.platform.datasource.SqlExecutor;

public class PostgresqlExecutor implements TenantQuerySqlExecutor {

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

  @Override
  public List<Map<String, String>> selectList(List<TenantIdentifier> tenantIdentifiers) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlTemplateBuilder =
        new StringBuilder(selectSql + " " + """
            WHERE id IN (
            """);

    List<Object> params = new ArrayList<>();
    for (TenantIdentifier tenantIdentifier : tenantIdentifiers) {

      if (!params.isEmpty()) {
        sqlTemplateBuilder.append(", ");
      }
      sqlTemplateBuilder.append("?::uuid");
      params.add(tenantIdentifier.value());
    }

    sqlTemplateBuilder.append(" )");
    String sqlTemplate = sqlTemplateBuilder.toString();

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}

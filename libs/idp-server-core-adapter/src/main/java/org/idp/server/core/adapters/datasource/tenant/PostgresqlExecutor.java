package org.idp.server.core.adapters.datasource.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.*;

public class PostgresqlExecutor implements TenantSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void insert(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO tenant(id, name, type, domain, attributes)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(tenant.name().value());
    params.add(tenant.type().name());
    params.add(tenant.domain().value());
    params.add(jsonConverter.write(tenant.attributesAsMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

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

  @Override
  public void update(Tenant tenant) {}

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {}
}

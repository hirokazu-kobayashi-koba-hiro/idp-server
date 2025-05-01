package org.idp.server.core.adapters.datasource.identity.permission;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.identity.permission.Permission;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements PermissionSqlExecutor {

  @Override
  public void insert(Tenant tenant, Permission permission) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO permission (id, tenant_id, name, description)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY
                UPDATE
                description = VALUES(description),
                updated_at = now();
                """;

    List<Object> params = new ArrayList<>();
    params.add(permission.id());
    params.add(tenant.identifierValue());
    params.add(permission.name());
    params.add(permission.description());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void bulkInsert(Tenant tenant, Permissions permissions) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                INSERT INTO permission (id, tenant_id, name, description)
                VALUES
                """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    permissions.forEach(
        permission -> {
          sqlValues.add("(?, ?, ?, ?)");
          params.add(permission.id());
          params.add(tenant.identifierValue());
          params.add(permission.name());
          params.add(permission.description());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }
}

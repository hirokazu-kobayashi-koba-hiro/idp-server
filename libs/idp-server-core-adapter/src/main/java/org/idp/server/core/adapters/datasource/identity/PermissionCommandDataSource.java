package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.oauth.identity.permission.Permission;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oauth.identity.permission.Permissions;
import org.idp.server.core.tenant.Tenant;

public class PermissionCommandDataSource implements PermissionCommandRepository {

  @Override
  public void register(Tenant tenant, Permission permission) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                INSERT INTO permission (id, tenant_id, name, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, name)
                DO UPDATE SET
                description = EXCLUDED.description,
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
  public void bulkRegister(Tenant tenant, Permissions permissions) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

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

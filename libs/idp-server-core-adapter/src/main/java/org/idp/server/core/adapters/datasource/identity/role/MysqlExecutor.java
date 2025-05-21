package org.idp.server.core.adapters.datasource.identity.role;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements RoleSqlExecutor {

  @Override
  public void insert(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO role (id, tenant_id, name, description)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY
                UPDATE
                description = VALUES(description),
                updated_at = now();
                """;

    List<Object> params = new ArrayList<>();
    params.add(role.id());
    params.add(tenant.identifierValue());
    params.add(role.name());
    params.add(role.description());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void bulkInsert(Tenant tenant, Roles roles) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    bulkRegisterRole(tenant, roles, sqlExecutor);
    bulkRegisterPermission(roles, sqlExecutor);
  }

  private void bulkRegisterRole(Tenant tenant, Roles roles, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                    INSERT INTO role (id, tenant_id, name, description)
                    VALUES
                    """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    roles.forEach(
        role -> {
          sqlValues.add("(?, ?, ?, ?)");
          params.add(role.id());
          params.add(tenant.identifierValue());
          params.add(role.name());
          params.add(role.description());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  private void bulkRegisterPermission(Roles roles, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT INTO role_permission (role_id, permission_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    roles.forEach(
        role ->
            role.permissions()
                .forEach(
                    permission -> {
                      sqlValues.add("(?, ?)");
                      params.add(role.id());
                      params.add(permission.id());
                    }));
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }
}

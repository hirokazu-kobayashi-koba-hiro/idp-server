package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.oauth.identity.role.Role;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;
import org.idp.server.core.oauth.identity.role.Roles;
import org.idp.server.core.tenant.Tenant;

public class RoleCommandDataSource implements RoleCommandRepository {

  @Override
  public void register(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO role (id, tenant_id, name, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, name)
                DO UPDATE SET
                description = EXCLUDED.description,
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
  public void bulkRegister(Tenant tenant, Roles roles) {
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

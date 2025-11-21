/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.identity.role.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements RoleSqlExecutor {

  @Override
  public void insert(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO role (id, tenant_id, name, description)
                VALUES (?::uuid, ?::uuid, ?, ?)
                ON CONFLICT (tenant_id, name)
                DO UPDATE SET
                description = EXCLUDED.description,
                updated_at = now();
                """;

    List<Object> params = new ArrayList<>();
    params.add(role.idAsUuid());
    params.add(tenant.identifierUUID());
    params.add(role.name());
    params.add(role.description());

    sqlExecutor.execute(sqlTemplate, params);
    registerPermission(tenant, role, sqlExecutor);
  }

  @Override
  public void bulkInsert(Tenant tenant, Roles roles) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    bulkRegisterRole(tenant, roles, sqlExecutor);
    bulkRegisterPermission(tenant, roles, sqlExecutor);
  }

  @Override
  public void update(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // 1. Update role table
    String updateRoleSql =
        """
                UPDATE role
                SET name= ?,
                description= ?
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid;
                """;

    List<Object> updateParams = new ArrayList<>();
    updateParams.add(role.name());
    updateParams.add(role.description());
    updateParams.add(role.idAsUuid());
    updateParams.add(tenant.identifierUUID());

    sqlExecutor.execute(updateRoleSql, updateParams);

    // 2. Delete all existing permissions
    String deletePermissionsSql =
        """
                DELETE FROM role_permission
                WHERE tenant_id = ?::uuid
                AND role_id = ?::uuid;
                """;

    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(tenant.identifierUUID());
    deleteParams.add(role.idAsUuid());

    sqlExecutor.execute(deletePermissionsSql, deleteParams);

    // 3. Register new permissions
    if (!role.permissions().isEmpty()) {
      registerPermission(tenant, role, sqlExecutor);
    }
  }

  @Override
  public void delete(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE FROM role
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(role.idAsUuid());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deletePermissions(Tenant tenant, Role role, Permissions removedTarget) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM role_permission
            WHERE tenant_id = ?::uuid
            AND role_id = ?::uuid
            AND permission_id IN (%s);
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(role.idAsUuid());

    String placeholders = String.join(",", Collections.nCopies(removedTarget.size(), "?::uuid"));
    String sql = String.format(sqlTemplate, placeholders);
    for (Permission permission : removedTarget) {
      params.add(permission.idAsUuid());
    }

    sqlExecutor.execute(sql, params);
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
          sqlValues.add("(?::uuid, ?::uuid, ?, ?)");
          params.add(role.idAsUuid());
          params.add(tenant.identifierUUID());
          params.add(role.name());
          params.add(role.description());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  private void bulkRegisterPermission(Tenant tenant, Roles roles, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT INTO role_permission (tenant_id, role_id, permission_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    roles.forEach(
        role ->
            role.permissions()
                .forEach(
                    permission -> {
                      sqlValues.add("(?::uuid, ?::uuid, ?::uuid)");
                      params.add(tenant.identifierUUID());
                      params.add(role.idAsUuid());
                      params.add(permission.idAsUuid());
                    }));
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON CONFLICT (role_id, permission_id) DO NOTHING;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  private void registerPermission(Tenant tenant, Role role, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                            INSERT INTO role_permission (tenant_id, role_id, permission_id)
                            VALUES
                            """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    role.permissions()
        .forEach(
            permission -> {
              sqlValues.add("(?::uuid, ?::uuid, ?::uuid)");
              params.add(tenant.identifierUUID());
              params.add(role.idAsUuid());
              params.add(permission.idAsUuid());
            });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON CONFLICT (role_id, permission_id) DO NOTHING;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }
}

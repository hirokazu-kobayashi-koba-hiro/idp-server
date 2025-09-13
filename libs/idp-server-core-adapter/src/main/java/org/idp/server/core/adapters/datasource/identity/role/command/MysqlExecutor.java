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
import java.util.UUID;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
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
    String sqlTemplate =
        """
                UPDATE role
                SET name= ?,
                description= ?
                WHERE id = ?
                AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(role.name());
    params.add(role.description());
    params.add(role.id());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
    registerPermission(tenant, role, sqlExecutor);
  }

  @Override
  public void delete(Tenant tenant, Role role) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE FROM role
                WHERE id = ?
                AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(role.id());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deletePermissions(Tenant tenant, Role role, Permissions removedTarget) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM role_permission
            WHERE tenant_id = ?
            AND role_id = ?
            AND permission_id IN (%s);
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(role.id());

    String placeholders = String.join(",", Collections.nCopies(removedTarget.size(), "?"));
    String sql = String.format(sqlTemplate, placeholders);
    for (Permission permission : removedTarget) {
      params.add(permission.id());
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

  private void bulkRegisterPermission(Tenant tenant, Roles roles, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT INTO role_permission (id, tenant_id, role_id, permission_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    roles.forEach(
        role ->
            role.permissions()
                .forEach(
                    permission -> {
                      sqlValues.add("(?, ?, ?, ?)");
                      params.add(UUID.randomUUID().toString());
                      params.add(tenant.identifier().value());
                      params.add(role.id());
                      params.add(permission.id());
                    }));
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON DUPLICATE KEY UPDATE id = id;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  private void registerPermission(Tenant tenant, Role role, SqlExecutor sqlExecutor) {
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                            INSERT INTO role_permission (id, tenant_id, role_id, permission_id)
                            VALUES
                            """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    role.permissions()
        .forEach(
            permission -> {
              sqlValues.add("(?, ?, ?, ?)");
              params.add(UUID.randomUUID().toString());
              params.add(tenant.identifier().value());
              params.add(role.id());
              params.add(permission.id());
            });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON DUPLICATE KEY UPDATE created_at = now();");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }
}

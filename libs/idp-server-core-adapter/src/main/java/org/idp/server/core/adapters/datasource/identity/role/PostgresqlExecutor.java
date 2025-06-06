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

package org.idp.server.core.adapters.datasource.identity.role;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.identity.role.Role;
import org.idp.server.core.oidc.identity.role.Roles;
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
  }

  @Override
  public void bulkInsert(Tenant tenant, Roles roles) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    bulkRegisterRole(tenant, roles, sqlExecutor);
    bulkRegisterPermission(tenant, roles, sqlExecutor);
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
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }
}

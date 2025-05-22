/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.permission;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.identity.permission.Permission;
import org.idp.server.core.oidc.identity.permission.Permissions;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements PermissionSqlExecutor {

  @Override
  public void insert(Tenant tenant, Permission permission) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO permission (
                id,
                tenant_id,
                name,
                description)
                VALUES (
                ?::uuid,
                ?::uuid,
                ?,
                ?)
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
  public void bulkInsert(Tenant tenant, Permissions permissions) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                INSERT INTO permission (
                id,
                tenant_id,
                name,
                description)
                VALUES
                """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    permissions.forEach(
        permission -> {
          sqlValues.add("(?::uuid, ?::uuid, ?, ?)");
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

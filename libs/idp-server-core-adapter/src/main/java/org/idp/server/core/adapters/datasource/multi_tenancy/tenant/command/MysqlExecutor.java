/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class MysqlExecutor implements TenantCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO tenant(
                id,
                name,
                type,
                domain,
                authorization_provider,
                database_type,
                attributes
                )
                VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
                )
                """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(tenant.name().value());
    params.add(tenant.type().name());
    params.add(tenant.domain().value());
    params.add(tenant.authorizationProvider().name());
    params.add(tenant.databaseType().name());
    params.add(jsonConverter.write(tenant.attributesAsMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant) {}

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {}
}

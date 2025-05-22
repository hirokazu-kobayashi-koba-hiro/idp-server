/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements ServerConfigSqlExecutor {

  JsonConverter jsonConverter;

  public PostgresqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO authorization_server_configuration (
                    tenant_id,
                    token_issuer,
                    payload
                    )
                    VALUES (?::uuid, ?, ?::jsonb);
                    """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        UPDATE authorization_server_configuration
                        SET payload = ?::jsonb,
                        token_issuer = ?
                        WHERE tenant_id = ?::uuid;
                        """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(payload);
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}

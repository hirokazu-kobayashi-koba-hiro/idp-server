/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthenticationInteractionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthorizationIdentifier identifier, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT authorization_id, payload
            FROM authentication_interactions
            WHERE authorization_id = ?
            AND tenant_id = ?
            AND interaction_type = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

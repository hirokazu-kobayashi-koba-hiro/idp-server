/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationConfigSqlExecutor {

  String selectSql =
      """
          SELECT id, payload
          FROM identity_verification_configurations \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, IdentityVerificationType type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?::uuid
            AND type = ?
            AND enabled = true
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier) {

    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                limit ?
                offset ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}

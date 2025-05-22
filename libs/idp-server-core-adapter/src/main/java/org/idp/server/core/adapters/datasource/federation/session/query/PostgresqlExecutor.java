/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;

public class PostgresqlExecutor implements SsoSessionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(SsoSessionIdentifier ssoSessionIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                SELECT
                id,
                payload
                FROM federation_sso_session
                WHERE id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(ssoSessionIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

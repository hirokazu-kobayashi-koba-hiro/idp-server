/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.verifiable_credentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements VerifiableCredentialTransactionSqlExecutor {

  @Override
  public void insert(
      Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO verifiable_credential_transaction
                    (id, tenant_id, credential_issuer, client_id, user_id, verifiable_credential, status)
                    VALUES (?::uuid, ?, ?, ?, ?::jsonb, ?);
                    """;
    List<Object> params = InsertSqlParamsCreator.create(verifiableCredentialTransaction);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, TransactionId transactionId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, credential_issuer, client_id, user_id, verifiable_credential, status
            FROM verifiable_credential_transaction
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid;
            """;
    List<Object> params = new ArrayList<>();
    params.add(transactionId.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

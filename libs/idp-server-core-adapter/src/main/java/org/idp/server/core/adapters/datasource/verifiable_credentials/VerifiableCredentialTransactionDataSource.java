/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.verifiable_credentials;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.core.extension.verifiable_credentials.repository.VerifiableCredentialTransactionRepository;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class VerifiableCredentialTransactionDataSource
    implements VerifiableCredentialTransactionRepository {

  VerifiableCredentialTransactionSqlExecutors executors;

  public VerifiableCredentialTransactionDataSource() {
    this.executors = new VerifiableCredentialTransactionSqlExecutors();
  }

  @Override
  public void register(
      Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, verifiableCredentialTransaction);
  }

  @Override
  public VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(tenant, transactionId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new NotFoundException(
          String.format("not found verifiable credential transaction (%s)", transactionId.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}

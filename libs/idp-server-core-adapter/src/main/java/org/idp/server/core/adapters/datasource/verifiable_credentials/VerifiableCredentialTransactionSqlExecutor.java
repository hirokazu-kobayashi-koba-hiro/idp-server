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
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface VerifiableCredentialTransactionSqlExecutor {

  void insert(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  Map<String, String> selectOne(Tenant tenant, TransactionId transactionId);
}

package org.idp.server.core.adapters.datasource.verifiable_credentials;

import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface VerifiableCredentialTransactionSqlExecutor {

  void insert(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  Map<String, String> selectOne(Tenant tenant, TransactionId transactionId);
}

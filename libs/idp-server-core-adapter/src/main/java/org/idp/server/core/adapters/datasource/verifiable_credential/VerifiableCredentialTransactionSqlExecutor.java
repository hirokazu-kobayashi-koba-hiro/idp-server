package org.idp.server.core.adapters.datasource.verifiable_credential;

import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionSqlExecutor {

  void insert(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  Map<String, String> selectOne(Tenant tenant, TransactionId transactionId);
}

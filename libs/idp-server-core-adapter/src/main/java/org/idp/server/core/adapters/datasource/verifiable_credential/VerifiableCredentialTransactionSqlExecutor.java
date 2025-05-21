package org.idp.server.core.adapters.datasource.verifiable_credential;

import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface VerifiableCredentialTransactionSqlExecutor {

  void insert(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  Map<String, String> selectOne(Tenant tenant, TransactionId transactionId);
}

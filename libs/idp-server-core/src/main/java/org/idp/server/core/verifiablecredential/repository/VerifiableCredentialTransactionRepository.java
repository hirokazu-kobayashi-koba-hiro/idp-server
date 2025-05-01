package org.idp.server.core.verifiablecredential.repository;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionRepository {

  void register(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId);
}

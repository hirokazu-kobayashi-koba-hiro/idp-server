package org.idp.server.core.verifiable_credential.repository;

import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionRepository {

  void register(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId);
}

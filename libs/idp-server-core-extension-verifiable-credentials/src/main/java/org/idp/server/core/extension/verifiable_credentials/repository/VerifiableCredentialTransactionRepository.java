package org.idp.server.core.extension.verifiable_credentials.repository;

import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface VerifiableCredentialTransactionRepository {

  void register(Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction);

  VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId);
}

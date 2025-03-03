package org.idp.server.core.verifiablecredential.repository;

import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionRepository {

  void register(VerifiableCredentialTransaction verifiableCredentialTransaction);

  VerifiableCredentialTransaction find(TransactionId transactionId);
}

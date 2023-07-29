package org.idp.server.verifiablecredential.repository;

import org.idp.server.type.verifiablecredential.TransactionId;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionRepository {

  void register(VerifiableCredentialTransaction verifiableCredentialTransaction);

  VerifiableCredentialTransaction find(TransactionId transactionId);
}

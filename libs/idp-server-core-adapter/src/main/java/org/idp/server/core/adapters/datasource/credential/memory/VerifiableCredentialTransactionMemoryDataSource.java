package org.idp.server.core.adapters.datasource.credential.memory;

import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class VerifiableCredentialTransactionMemoryDataSource
    implements VerifiableCredentialTransactionRepository {

  Map<TransactionId, VerifiableCredentialTransaction> values = new HashMap<>();

  @Override
  public void register(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    values.put(verifiableCredentialTransaction.transactionId(), verifiableCredentialTransaction);
  }

  @Override
  public VerifiableCredentialTransaction find(TransactionId transactionId) {
    VerifiableCredentialTransaction verifiableCredentialTransaction = values.get(transactionId);
    if (Objects.isNull(verifiableCredentialTransaction)) {
      return new VerifiableCredentialTransaction();
    }
    return verifiableCredentialTransaction;
  }
}

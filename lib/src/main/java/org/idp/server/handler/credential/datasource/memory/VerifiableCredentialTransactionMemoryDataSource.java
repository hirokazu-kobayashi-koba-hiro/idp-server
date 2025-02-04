package org.idp.server.handler.credential.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.type.verifiablecredential.TransactionId;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

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

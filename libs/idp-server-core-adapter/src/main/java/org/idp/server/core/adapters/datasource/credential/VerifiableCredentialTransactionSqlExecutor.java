package org.idp.server.core.adapters.datasource.credential;

import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;

public interface VerifiableCredentialTransactionSqlExecutor {

  void insert(VerifiableCredentialTransaction verifiableCredentialTransaction);

  Map<String, String> selectOne(TransactionId transactionId);
}

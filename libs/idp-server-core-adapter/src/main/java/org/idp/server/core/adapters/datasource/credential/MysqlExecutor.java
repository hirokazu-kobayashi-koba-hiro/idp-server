package org.idp.server.core.adapters.datasource.credential;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;

public class MysqlExecutor implements VerifiableCredentialTransactionSqlExecutor {

  @Override
  public void insert(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.verifiable_credential_transaction
                    (transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status)
                    VALUES (?, ?, ?, ?, ?, ?);
                    """;
    List<Object> params = InsertSqlParamsCreator.create(verifiableCredentialTransaction);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(TransactionId transactionId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status
            FROM verifiable_credential_transaction
            WHERE transaction_id = ?;
            """;
    List<Object> params = List.of(transactionId.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

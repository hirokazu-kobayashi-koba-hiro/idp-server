package org.idp.server.handler.credential.datasource.database;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.type.verifiablecredential.TransactionId;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

public class VerifiableCredentialTransactionDataSource
    implements VerifiableCredentialTransactionRepository {

  SqlConnection sqlConnection;

  public VerifiableCredentialTransactionDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
  }

  @Override
  public void register(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    String sql = InsertSqlCreator.createInsert(verifiableCredentialTransaction);
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    sqlExecutor.execute(sql);
  }

  @Override
  public VerifiableCredentialTransaction find(TransactionId transactionId) {
    String sqlTemplate =
        """
            SELECT transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status
            FROM verifiable_credential_transaction
            WHERE transaction_id = '%s';
            """;
    String sql = String.format(sqlTemplate, transactionId.value());
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new RuntimeException(
          String.format("not found verifiable credential transaction (%s)", transactionId.value()));
    }
    return ModelConverter.convert(stringMap);
  }
}

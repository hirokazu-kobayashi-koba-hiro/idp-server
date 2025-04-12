package org.idp.server.core.adapters.datasource.credential;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.type.exception.NotFoundException;
import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

public class VerifiableCredentialTransactionDataSource
    implements VerifiableCredentialTransactionRepository {

  @Override
  public void register(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.verifiable_credential_transaction
                    (transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?);
                    """;
    List<Object> params = InsertSqlParamsCreator.create(verifiableCredentialTransaction);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public VerifiableCredentialTransaction find(TransactionId transactionId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status
            FROM verifiable_credential_transaction
            WHERE transaction_id = ?;
            """;
    List<Object> params = List.of(transactionId.value());

    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new NotFoundException(
          String.format("not found verifiable credential transaction (%s)", transactionId.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}

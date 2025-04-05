package org.idp.server.core.adapters.datasource.mfa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.mfa.MfaTransactionIdentifier;
import org.idp.server.core.mfa.MfaTransactionQueryRepository;
import org.idp.server.core.mfa.exception.MfaTransactionNotFoundException;

public class MfaTransactionQueryDataSource implements MfaTransactionQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(MfaTransactionIdentifier identifier, String key, Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT id, payload
            FROM mfa_transactions
            WHERE id = ?
            AND key = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaTransactionNotFoundException(
          String.format("Mfa transaction is Not Found (%s) (%s)", identifier.value(), key));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}

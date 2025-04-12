package org.idp.server.core.adapters.datasource.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.authentication.exception.MfaTransactionNotFoundException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;

public class AuthenticationTransactionQueryDataSource
    implements AuthenticationTransactionQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(AuthenticationTransactionIdentifier identifier, String type, Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT id, payload
            FROM authentication_transactions
            WHERE id = ?
            AND type = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(type);

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaTransactionNotFoundException(
          String.format("Mfa transaction is Not Found (%s) (%s)", identifier.value(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}

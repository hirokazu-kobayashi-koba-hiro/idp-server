package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;

public class PostgresqlExecutor implements AuthenticationTransactionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> void insert(AuthenticationTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO authentication_transactions (id, type, payload) VALUES (?, ?, ?::jsonb)
            ON CONFLICT (id, type) DO UPDATE SET payload = ?::jsonb, updated_at = now()
            """;

    String json = jsonConverter.write(payload);

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(type);
    params.add(json);
    params.add(json);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public <T> void update(AuthenticationTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authentication_transactions
                SET payload = ?::jsonb,
                updated_at = now()
                WHERE id = ?
                AND type = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(payload));
    params.add(identifier.value());
    params.add(type);

    sqlExecutor.execute(sqlTemplate, params);
  }
}

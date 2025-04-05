package org.idp.server.core.adapters.datasource.mfa;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.mfa.MfaTransactionCommandRepository;
import org.idp.server.core.mfa.MfaTransactionIdentifier;

public class MfaTransactionCommandDataSource implements MfaTransactionCommandRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> void register(MfaTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            INSERT INTO mfa_transactions (id, type, payload) VALUES (?, ?, ?::jsonb)
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
  public <T> void update(MfaTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                UPDATE mfa_transactions
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

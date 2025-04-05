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
  public <T> void register(MfaTransactionIdentifier identifier, String key, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            INSERt INTO mfa_transactions (id, key, payload) VALUES (?, ?, ?)
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(key);
    params.add(jsonConverter.write(payload));

    sqlExecutor.execute(sqlTemplate, params);
  }
}

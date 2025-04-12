package org.idp.server.core.adapters.datasource.federation;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.SsoSessionCommandRepository;
import org.idp.server.core.federation.SsoSessionIdentifier;

public class SsoSessionCommandDataSource implements SsoSessionCommandRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> void register(SsoSessionIdentifier identifier, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                INSERT INTO federation_sso_session (id, payload)
                VALUES (?, ?::jsonb)
                ON CONFLICT (id) DO
                UPDATE SET payload = ?::jsonb, updated_at = now();
                """;

    String json = jsonConverter.write(payload);
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(json);
    params.add(json);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(SsoSessionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                DELETE FROM federation_sso_session
                WHERE id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}

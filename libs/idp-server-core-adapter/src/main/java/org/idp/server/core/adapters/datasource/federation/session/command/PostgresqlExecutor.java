package org.idp.server.core.adapters.datasource.federation.session.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.federation.SsoSessionIdentifier;

public class PostgresqlExecutor implements SsoSessionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> void insert(SsoSessionIdentifier identifier, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

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
    SqlExecutor sqlExecutor = new SqlExecutor();

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

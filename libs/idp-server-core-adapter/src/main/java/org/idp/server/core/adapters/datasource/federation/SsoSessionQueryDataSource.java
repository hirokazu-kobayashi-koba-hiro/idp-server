package org.idp.server.core.adapters.datasource.federation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.SsoSessionNotFoundException;
import org.idp.server.core.federation.SsoSessionQueryRepository;
import org.idp.server.core.type.oauth.State;

public class SsoSessionQueryDataSource implements SsoSessionQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(State state, Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                SELECT id, payload
                FROM federation_sso_session
                WHERE id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(state.value());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new SsoSessionNotFoundException(
          String.format("federation sso session is not found (%s)", state.value()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}

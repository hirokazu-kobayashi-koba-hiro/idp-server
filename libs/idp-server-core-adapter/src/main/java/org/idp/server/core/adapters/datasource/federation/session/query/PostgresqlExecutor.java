package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;

public class PostgresqlExecutor implements SsoSessionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(SsoSessionIdentifier ssoSessionIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                SELECT
                id,
                payload
                FROM federation_sso_session
                WHERE id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(ssoSessionIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

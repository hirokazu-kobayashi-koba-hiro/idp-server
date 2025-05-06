package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.federation.SsoSessionIdentifier;

public class MysqlExecutor implements SsoSessionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(SsoSessionIdentifier ssoSessionIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = """
        SELECT id, payload
        FROM federation_sso_session
        WHERE id = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(ssoSessionIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

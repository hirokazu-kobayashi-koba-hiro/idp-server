package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.sql.SqlExecutor;

public class MysqlExecutor implements AuthenticationTransactionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      AuthenticationTransactionIdentifier identifier, String type) {
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

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}

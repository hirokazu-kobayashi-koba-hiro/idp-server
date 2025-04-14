package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;

public interface AuthenticationTransactionQuerySqlExecutor {
  Map<String, String> selectOne(AuthenticationTransactionIdentifier identifier, String type);
}

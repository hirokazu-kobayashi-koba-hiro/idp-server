package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;

import java.util.Map;

public interface AuthenticationTransactionQuerySqlExecutor {
    Map<String, String> selectOne(AuthenticationTransactionIdentifier identifier, String type);
}

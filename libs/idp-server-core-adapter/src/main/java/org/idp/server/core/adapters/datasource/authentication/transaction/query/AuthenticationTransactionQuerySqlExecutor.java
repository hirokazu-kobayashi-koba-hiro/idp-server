package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationTransactionQuerySqlExecutor {

  Map<String, String> selectOne(Tenant tenant, AuthenticationTransactionIdentifier identifier);
}

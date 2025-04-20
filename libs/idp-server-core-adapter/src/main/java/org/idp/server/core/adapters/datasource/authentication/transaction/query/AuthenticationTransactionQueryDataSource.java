package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.authentication.AuthenticationTransactionNotFoundException;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationTransactionQueryDataSource
    implements AuthenticationTransactionQueryRepository {

  AuthenticationTransactionQuerySqlExecutors executors;

  public AuthenticationTransactionQueryDataSource() {
    this.executors = new AuthenticationTransactionQuerySqlExecutors();
  }

  @Override
  public AuthenticationTransaction get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new AuthenticationTransactionNotFoundException(
          "Authentication transaction not found for identifier: " + identifier.value());
    }

    return ModelConverter.convert(result);
  }
}

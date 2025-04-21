package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionNotFoundException;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.oauth.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationTransactionQueryDataSource
    implements AuthenticationTransactionQueryRepository {

  AuthenticationTransactionQuerySqlExecutors executors;

  public AuthenticationTransactionQueryDataSource() {
    this.executors = new AuthenticationTransactionQuerySqlExecutors();
  }

  @Override
  public AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier) {
    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new AuthenticationTransactionNotFoundException(
          "Authentication transaction not found for identifier: " + identifier.value());
    }

    return ModelConverter.convert(result);
  }

  @Override
  public AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier) {

    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result =
        executor.selectOneByDeviceId(tenant, authenticationDeviceIdentifier);

    if (result == null || result.isEmpty()) {
      return new AuthenticationTransaction();
    }

    return ModelConverter.convert(result);
  }
}

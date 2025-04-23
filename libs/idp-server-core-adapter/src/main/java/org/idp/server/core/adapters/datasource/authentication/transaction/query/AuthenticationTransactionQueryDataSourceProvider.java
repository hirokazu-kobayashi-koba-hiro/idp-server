package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;

public class AuthenticationTransactionQueryDataSourceProvider
    implements ApplicationComponentProvider<AuthenticationTransactionQueryRepository> {

  @Override
  public Class<AuthenticationTransactionQueryRepository> type() {
    return AuthenticationTransactionQueryRepository.class;
  }

  @Override
  public AuthenticationTransactionQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthenticationTransactionQueryDataSource();
  }
}

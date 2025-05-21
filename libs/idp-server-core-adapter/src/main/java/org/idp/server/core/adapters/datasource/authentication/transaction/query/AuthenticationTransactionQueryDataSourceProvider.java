package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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

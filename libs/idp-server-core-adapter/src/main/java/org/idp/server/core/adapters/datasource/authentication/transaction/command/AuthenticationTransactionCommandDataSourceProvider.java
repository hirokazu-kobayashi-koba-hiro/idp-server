package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthenticationTransactionCommandDataSourceProvider
    implements ApplicationComponentProvider<AuthenticationTransactionCommandRepository> {

  @Override
  public Class<AuthenticationTransactionCommandRepository> type() {
    return AuthenticationTransactionCommandRepository.class;
  }

  @Override
  public AuthenticationTransactionCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthenticationTransactionCommandDataSource();
  }
}

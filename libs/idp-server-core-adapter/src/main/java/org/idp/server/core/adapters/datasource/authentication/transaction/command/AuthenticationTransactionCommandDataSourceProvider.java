package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.authentication.repository.AuthenticationTransactionCommandRepository;

public class AuthenticationTransactionCommandDataSourceProvider implements ApplicationComponentProvider<AuthenticationTransactionCommandRepository> {

  @Override
  public Class<AuthenticationTransactionCommandRepository> type() {
    return AuthenticationTransactionCommandRepository.class;
  }

  @Override
  public AuthenticationTransactionCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new AuthenticationTransactionCommandDataSource();
  }
}

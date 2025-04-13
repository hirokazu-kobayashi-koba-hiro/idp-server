package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationDependencyProvider;
import org.idp.server.core.authentication.AuthenticationTransactionCommandRepository;

public class AuthenticationTransactionCommandDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationTransactionCommandRepository> {

  @Override
  public Class<AuthenticationTransactionCommandRepository> type() {
    return AuthenticationTransactionCommandRepository.class;
  }

  @Override
  public AuthenticationTransactionCommandRepository provide() {
    return new AuthenticationTransactionCommandDataSource();
  }
}

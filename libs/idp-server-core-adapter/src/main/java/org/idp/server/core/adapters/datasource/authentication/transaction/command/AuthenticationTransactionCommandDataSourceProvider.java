package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.authentication.AuthenticationDependencyProvider;
import org.idp.server.core.authentication.AuthenticationInteractionCommandRepository;

public class AuthenticationTransactionCommandDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationInteractionCommandRepository> {

  @Override
  public Class<AuthenticationInteractionCommandRepository> type() {
    return AuthenticationInteractionCommandRepository.class;
  }

  @Override
  public AuthenticationInteractionCommandRepository provide() {
    return new AuthenticationInteractionCommandDataSource();
  }
}

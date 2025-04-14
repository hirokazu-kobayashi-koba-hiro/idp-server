package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import org.idp.server.core.authentication.AuthenticationDependencyProvider;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;

public class AuthenticationTransactionQueryDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationTransactionQueryRepository> {

  @Override
  public Class<AuthenticationTransactionQueryRepository> type() {
    return AuthenticationTransactionQueryRepository.class;
  }

  @Override
  public AuthenticationTransactionQueryRepository provide() {
    return new AuthenticationTransactionQueryDataSource();
  }
}

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import org.idp.server.core.authentication.AuthenticationDependencyProvider;
import org.idp.server.core.authentication.AuthenticationInteractionQueryRepository;

public class AuthenticationTransactionQueryDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationInteractionQueryRepository> {

  @Override
  public Class<AuthenticationInteractionQueryRepository> type() {
    return AuthenticationInteractionQueryRepository.class;
  }

  @Override
  public AuthenticationInteractionQueryRepository provide() {
    return new AuthenticationInteractionQueryDataSource();
  }
}

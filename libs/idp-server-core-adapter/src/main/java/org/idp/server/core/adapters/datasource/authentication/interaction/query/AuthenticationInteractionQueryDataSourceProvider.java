package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;

public class AuthenticationInteractionQueryDataSourceProvider
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

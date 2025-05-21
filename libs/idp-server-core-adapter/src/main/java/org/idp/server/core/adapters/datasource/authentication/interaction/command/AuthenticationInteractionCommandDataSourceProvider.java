package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;

public class AuthenticationInteractionCommandDataSourceProvider
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

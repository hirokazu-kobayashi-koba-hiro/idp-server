package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationCommandRepository;

public class IdentityVerificationApplicationCommandDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationApplicationCommandRepository> {

  @Override
  public Class<IdentityVerificationApplicationCommandRepository> type() {
    return IdentityVerificationApplicationCommandRepository.class;
  }

  @Override
  public IdentityVerificationApplicationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationApplicationCommandDataSource();
  }
}

package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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

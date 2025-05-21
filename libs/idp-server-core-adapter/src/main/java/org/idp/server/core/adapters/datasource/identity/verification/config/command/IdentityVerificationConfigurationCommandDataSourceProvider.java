package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;

public class IdentityVerificationConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationConfigurationCommandRepository> {

  @Override
  public Class<IdentityVerificationConfigurationCommandRepository> type() {
    return IdentityVerificationConfigurationCommandRepository.class;
  }

  @Override
  public IdentityVerificationConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationConfigurationCommandDataSource();
  }
}

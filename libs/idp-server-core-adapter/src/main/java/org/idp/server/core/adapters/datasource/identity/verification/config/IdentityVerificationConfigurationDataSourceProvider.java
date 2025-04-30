package org.idp.server.core.adapters.datasource.identity.verification.config;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;

public class IdentityVerificationConfigurationDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationConfigurationQueryRepository> {

  @Override
  public Class<IdentityVerificationConfigurationQueryRepository> type() {
    return IdentityVerificationConfigurationQueryRepository.class;
  }

  @Override
  public IdentityVerificationConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationConfigurationQueryDataSource();
  }
}

package org.idp.server.core.adapters.datasource.identity.trustframework.config;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.trustframework.IdentityVerificationConfigurationQueryRepository;

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

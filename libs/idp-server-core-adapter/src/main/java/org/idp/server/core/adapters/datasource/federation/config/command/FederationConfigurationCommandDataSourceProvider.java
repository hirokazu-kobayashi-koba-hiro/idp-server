package org.idp.server.core.adapters.datasource.federation.config.command;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.federation.repository.FederationConfigurationCommandRepository;

public class FederationConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<FederationConfigurationCommandRepository> {

  @Override
  public Class<FederationConfigurationCommandRepository> type() {
    return FederationConfigurationCommandRepository.class;
  }

  @Override
  public FederationConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new FederationConfigurationCommandDataSource();
  }
}

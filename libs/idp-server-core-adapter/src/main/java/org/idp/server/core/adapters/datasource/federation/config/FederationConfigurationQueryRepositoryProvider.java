package org.idp.server.core.adapters.datasource.federation.config;

import org.idp.server.core.federation.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.FederationDependencyProvider;

public class FederationConfigurationQueryRepositoryProvider implements FederationDependencyProvider<FederationConfigurationQueryRepository> {

  @Override
  public Class<FederationConfigurationQueryRepository> type() {
    return FederationConfigurationQueryRepository.class;
  }

  @Override
  public FederationConfigurationQueryRepository provide() {
    return new FederationConfigurationQueryDataSource();
  }
}

package org.idp.server.core.adapters.datasource.federation.config.query;

import org.idp.server.core.federation.factory.FederationDependencyProvider;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;

public class FederationConfigurationQueryDataSourceProvider
    implements FederationDependencyProvider<FederationConfigurationQueryRepository> {

  @Override
  public Class<FederationConfigurationQueryRepository> type() {
    return FederationConfigurationQueryRepository.class;
  }

  @Override
  public FederationConfigurationQueryRepository provide() {
    return new FederationConfigurationQueryDataSource();
  }
}

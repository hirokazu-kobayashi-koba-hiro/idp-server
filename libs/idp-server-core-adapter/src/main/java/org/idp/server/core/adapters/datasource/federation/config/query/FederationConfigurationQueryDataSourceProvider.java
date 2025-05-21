package org.idp.server.core.adapters.datasource.federation.config.query;

import org.idp.server.core.oidc.federation.plugin.FederationDependencyProvider;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationQueryRepository;

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

package org.idp.server.core.adapters.datasource.federation.config.query;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;

public class FederationConfigurationQueryDataSourceAppProvider
    implements ApplicationComponentProvider<FederationConfigurationQueryRepository> {

  @Override
  public Class<FederationConfigurationQueryRepository> type() {
    return FederationConfigurationQueryRepository.class;
  }

  @Override
  public FederationConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new FederationConfigurationQueryDataSource();
  }
}

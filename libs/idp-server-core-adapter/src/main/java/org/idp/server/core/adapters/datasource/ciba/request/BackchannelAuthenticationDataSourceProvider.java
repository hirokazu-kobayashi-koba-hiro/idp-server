package org.idp.server.core.adapters.datasource.ciba.request;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;

public class BackchannelAuthenticationDataSourceProvider
    implements ApplicationComponentProvider<BackchannelAuthenticationRequestRepository> {

  @Override
  public Class<BackchannelAuthenticationRequestRepository> type() {
    return BackchannelAuthenticationRequestRepository.class;
  }

  @Override
  public BackchannelAuthenticationRequestRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new BackchannelAuthenticationDataSource();
  }
}

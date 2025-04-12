package org.idp.server.core.adapters.datasource.ciba.request;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;

public class BackchannelAuthenticationDataSourceProvider
    implements DataSourceProvider<BackchannelAuthenticationRequestRepository> {

  @Override
  public Class<BackchannelAuthenticationRequestRepository> type() {
    return BackchannelAuthenticationRequestRepository.class;
  }

  @Override
  public BackchannelAuthenticationRequestRepository provide(
      DataSourceDependencyContainer container) {
    return new BackchannelAuthenticationDataSource();
  }
}

package org.idp.server.core.adapters.datasource.ciba.request;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;

public class BackchannelAuthenticationDataSourceDependencyProvider
    implements DataSourceDependencyProvider<BackchannelAuthenticationRequestRepository> {

  @Override
  public Class<BackchannelAuthenticationRequestRepository> type() {
    return BackchannelAuthenticationRequestRepository.class;
  }

  @Override
  public BackchannelAuthenticationRequestRepository provide() {
    return new BackchannelAuthenticationDataSource();
  }
}

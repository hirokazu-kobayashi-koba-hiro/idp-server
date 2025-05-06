package org.idp.server.core.adapters.datasource.federation.session.query;

import org.idp.server.core.federation.FederationDependencyProvider;
import org.idp.server.core.federation.SsoSessionQueryRepository;

public class SsoSessionQueryDataSourceProvider implements FederationDependencyProvider<SsoSessionQueryRepository> {

  @Override
  public Class<SsoSessionQueryRepository> type() {
    return SsoSessionQueryRepository.class;
  }

  @Override
  public SsoSessionQueryRepository provide() {
    return new SsoSessionQueryDataSource();
  }
}

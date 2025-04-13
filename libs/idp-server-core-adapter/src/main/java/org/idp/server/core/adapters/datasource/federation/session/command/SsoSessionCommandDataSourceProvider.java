package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.federation.FederationDependencyProvider;
import org.idp.server.core.federation.SsoSessionCommandRepository;

public class SsoSessionCommandDataSourceProvider
    implements FederationDependencyProvider<SsoSessionCommandRepository> {

  @Override
  public Class<SsoSessionCommandRepository> type() {
    return SsoSessionCommandRepository.class;
  }

  @Override
  public SsoSessionCommandRepository provide() {
    return new SsoSessionCommandDataSource();
  }
}

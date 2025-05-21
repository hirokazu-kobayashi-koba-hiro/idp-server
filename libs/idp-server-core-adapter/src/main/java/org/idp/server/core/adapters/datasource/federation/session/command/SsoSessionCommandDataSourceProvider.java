package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.oidc.federation.plugin.FederationDependencyProvider;
import org.idp.server.core.oidc.federation.sso.SsoSessionCommandRepository;

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

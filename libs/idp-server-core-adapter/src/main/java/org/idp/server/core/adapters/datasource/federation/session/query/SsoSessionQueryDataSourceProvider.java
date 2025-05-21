package org.idp.server.core.adapters.datasource.federation.session.query;

import org.idp.server.core.oidc.federation.plugin.FederationDependencyProvider;
import org.idp.server.core.oidc.federation.sso.SsoSessionQueryRepository;

public class SsoSessionQueryDataSourceProvider
    implements FederationDependencyProvider<SsoSessionQueryRepository> {

  @Override
  public Class<SsoSessionQueryRepository> type() {
    return SsoSessionQueryRepository.class;
  }

  @Override
  public SsoSessionQueryRepository provide() {
    return new SsoSessionQueryDataSource();
  }
}

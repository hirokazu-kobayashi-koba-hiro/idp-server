package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationQueryRepository;

public class IdentityVerificationApplicationQueryDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationApplicationQueryRepository> {

  @Override
  public Class<IdentityVerificationApplicationQueryRepository> type() {
    return IdentityVerificationApplicationQueryRepository.class;
  }

  @Override
  public IdentityVerificationApplicationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationApplicationQueryDataSource();
  }
}

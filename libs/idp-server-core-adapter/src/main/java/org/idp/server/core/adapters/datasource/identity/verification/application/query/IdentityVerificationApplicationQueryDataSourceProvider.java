package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueryRepository;

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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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

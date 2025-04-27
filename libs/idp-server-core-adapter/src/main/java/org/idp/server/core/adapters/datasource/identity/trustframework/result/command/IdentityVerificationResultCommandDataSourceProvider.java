package org.idp.server.core.adapters.datasource.identity.trustframework.result.command;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.trustframework.result.IdentityVerificationResultCommandRepository;

public class IdentityVerificationResultCommandDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationResultCommandRepository> {

  @Override
  public Class<IdentityVerificationResultCommandRepository> type() {
    return IdentityVerificationResultCommandRepository.class;
  }

  @Override
  public IdentityVerificationResultCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationResultCommandDataSource();
  }
}

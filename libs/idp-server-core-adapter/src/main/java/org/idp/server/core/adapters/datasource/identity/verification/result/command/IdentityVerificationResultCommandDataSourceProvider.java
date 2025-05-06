package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;

public class IdentityVerificationResultCommandDataSourceProvider implements ApplicationComponentProvider<IdentityVerificationResultCommandRepository> {

  @Override
  public Class<IdentityVerificationResultCommandRepository> type() {
    return IdentityVerificationResultCommandRepository.class;
  }

  @Override
  public IdentityVerificationResultCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationResultCommandDataSource();
  }
}

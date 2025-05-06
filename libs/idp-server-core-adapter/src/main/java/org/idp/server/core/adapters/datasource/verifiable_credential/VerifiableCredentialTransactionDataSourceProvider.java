package org.idp.server.core.adapters.datasource.verifiable_credential;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.verifiable_credential.repository.VerifiableCredentialTransactionRepository;

public class VerifiableCredentialTransactionDataSourceProvider implements ApplicationComponentProvider<VerifiableCredentialTransactionRepository> {

  @Override
  public Class<VerifiableCredentialTransactionRepository> type() {
    return VerifiableCredentialTransactionRepository.class;
  }

  @Override
  public VerifiableCredentialTransactionRepository provide(ApplicationComponentDependencyContainer container) {
    return new VerifiableCredentialTransactionDataSource();
  }
}

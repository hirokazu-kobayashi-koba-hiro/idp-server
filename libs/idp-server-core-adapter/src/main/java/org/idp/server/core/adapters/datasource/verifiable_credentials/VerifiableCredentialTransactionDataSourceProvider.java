package org.idp.server.core.adapters.datasource.verifiable_credentials;

import org.idp.server.core.extension.verifiable_credentials.repository.VerifiableCredentialTransactionRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class VerifiableCredentialTransactionDataSourceProvider
    implements ApplicationComponentProvider<VerifiableCredentialTransactionRepository> {

  @Override
  public Class<VerifiableCredentialTransactionRepository> type() {
    return VerifiableCredentialTransactionRepository.class;
  }

  @Override
  public VerifiableCredentialTransactionRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new VerifiableCredentialTransactionDataSource();
  }
}

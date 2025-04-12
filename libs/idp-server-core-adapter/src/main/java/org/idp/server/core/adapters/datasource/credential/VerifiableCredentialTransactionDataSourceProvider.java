package org.idp.server.core.adapters.datasource.credential;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

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

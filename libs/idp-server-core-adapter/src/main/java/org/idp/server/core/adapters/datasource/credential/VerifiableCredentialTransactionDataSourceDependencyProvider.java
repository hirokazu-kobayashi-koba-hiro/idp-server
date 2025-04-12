package org.idp.server.core.adapters.datasource.credential;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

public class VerifiableCredentialTransactionDataSourceDependencyProvider
    implements DataSourceDependencyProvider<VerifiableCredentialTransactionRepository> {

  @Override
  public Class<VerifiableCredentialTransactionRepository> type() {
    return VerifiableCredentialTransactionRepository.class;
  }

  @Override
  public VerifiableCredentialTransactionRepository provide() {
    return new VerifiableCredentialTransactionDataSource();
  }
}

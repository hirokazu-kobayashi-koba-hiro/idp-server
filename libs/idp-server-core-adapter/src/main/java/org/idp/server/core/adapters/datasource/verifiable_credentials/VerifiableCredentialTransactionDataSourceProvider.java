/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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

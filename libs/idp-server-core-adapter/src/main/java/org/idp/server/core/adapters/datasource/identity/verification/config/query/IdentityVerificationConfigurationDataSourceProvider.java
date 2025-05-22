/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class IdentityVerificationConfigurationDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationConfigurationQueryRepository> {

  @Override
  public Class<IdentityVerificationConfigurationQueryRepository> type() {
    return IdentityVerificationConfigurationQueryRepository.class;
  }

  @Override
  public IdentityVerificationConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationConfigurationQueryDataSource();
  }
}

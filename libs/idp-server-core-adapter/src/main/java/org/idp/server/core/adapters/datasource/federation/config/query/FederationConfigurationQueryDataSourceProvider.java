/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.federation.config.query;

import org.idp.server.core.oidc.federation.plugin.FederationDependencyProvider;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationQueryRepository;

public class FederationConfigurationQueryDataSourceProvider
    implements FederationDependencyProvider<FederationConfigurationQueryRepository> {

  @Override
  public Class<FederationConfigurationQueryRepository> type() {
    return FederationConfigurationQueryRepository.class;
  }

  @Override
  public FederationConfigurationQueryRepository provide() {
    return new FederationConfigurationQueryDataSource();
  }
}
